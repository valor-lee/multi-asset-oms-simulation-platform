package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.execution.model.OrderSubmissionException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderSubmissionService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderSubmissionService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * 저장소에서 order를 조회한 뒤 broker/exchange 전송 상태로 전이한다.
     * 상태별 처리:
     * - CREATED: SENT로 전이해 저장
     * - SENT: 중복 요청으로 보고 기존 order 반환
     * - 그 외 상태: 전송 대상이 아니므로 예외
     *
     * @param orderId 전송 처리할 order id
     * @return SENT 상태로 저장된 order
     */
    public Order submit(UUID orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("order not found"));
        return submit(order);
    }

    /**
     * CREATED order를 SENT 상태로 전이한다.
     * 이미 SENT 상태이면 중복 전송 요청으로 보고 기존 order를 반환한다.
     * 상태별 처리:
     * - CREATED: updatedAt을 submittedAt으로 갱신하고 SENT로 저장
     * - SENT: 최초 전송 시각 보존을 위해 다시 저장하지 않음
     * - 그 외 상태: 예외
     *
     * @param order 전송 처리할 order
     * @return SENT 상태의 order
     */
    public Order submit(Order order) {
        if (order.status() == OrderStatus.SENT) {
            return order;
        }
        validateCreated(order);

        Instant submittedAt = Instant.now(clock);
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
                OrderStatus.SENT,
                order.createdAt(),
                submittedAt
        ));
    }

    private void validateCreated(Order order) {
        if (order.status() != OrderStatus.CREATED) {
            throw new OrderSubmissionException("only CREATED orders can be submitted");
        }
    }
}
