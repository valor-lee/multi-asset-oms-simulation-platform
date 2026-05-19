package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderAcknowledgementException;
import com.multiassetoms.execution.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderAcknowledgementService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderAcknowledgementService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * broker/exchange가 주문을 접수했음을 반영한다.
     * 상태별 처리:
     * - SENT -> ACKED로 전이해 저장
     * - ACKED: 중복 ack로 보고 기존 order 반환
     * - 그 외 상태: ack 대상이 아니므로 예외
     *
     * @param orderId ack 처리할 order id
     * @return ACKED 상태의 order
     */
    public Order acknowledge(UUID orderId) {
        Order order = findOrder(orderId);

        if (order.status() == OrderStatus.ACKED)
            return order;

        validateSent(order);

        return transition(order, OrderStatus.ACKED);
    }

    /**
     * broker/exchange가 주문을 거절했음을 반영한다.
     * 상태별 처리:
     * - SENT -> REJECTED로 전이해 저장
     * - REJECTED: 중복 reject로 보고 기존 order 반환
     * - 그 외 상태: reject 대상이 아니므로 예외
     *
     * @param orderId reject 처리할 order id
     * @return REJECTED 상태의 order
     */
    public Order reject(UUID orderId) {
        Order order = findOrder(orderId);

        if (order.status() == OrderStatus.REJECTED)
            return order;

        validateSent(order);

        return transition(order, OrderStatus.REJECTED);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderAcknowledgementException("order not found"));
    }

    private void validateSent(Order order) {
        if (order.status() != OrderStatus.SENT) {
            throw new OrderAcknowledgementException("only SENT orders can be acknowledged or rejected");
        }
    }

    private Order transition(Order order, OrderStatus nextStatus) {
        Instant acknowledgedAt = Instant.now(clock);

        return orderRepository.save(new Order(
                order.orderId(),
                order.intentId(),
                order.portfolioId(),
                order.instrumentId(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.filledQuantity(),
                order.limitPrice(),
                order.timeInForce(),
                nextStatus,
                order.createdAt(),
                acknowledgedAt
        ));
    }
}
