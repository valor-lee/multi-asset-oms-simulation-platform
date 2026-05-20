package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillException;
import com.multiassetoms.execution.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderFillService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderFillService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /**
     * broker/exchange 체결 수량을 order에 누적 반영한다.
     * 상태별 처리:
     * - ACKED: 첫 체결을 반영해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - PARTIALLY_FILLED: 추가 체결을 누적해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - CANCEL_REQUESTED: cancel-fill race condition을 허용해 추가 체결을 반영
     * - 그 외 상태: 체결 반영 대상이 아니므로 예외
     *
     * @param orderId 체결을 반영할 order id
     * @param fillQuantity 이번에 추가로 체결된 수량
     * @return 체결 수량과 상태가 갱신된 order
     */
    public Order fill(UUID orderId, BigDecimal fillQuantity) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderFillException("order not found"));

        validateFillable(order);
        validateFillQuantity(fillQuantity);

        BigDecimal newFilledQuantity = order.filledQuantity().add(fillQuantity);
        validateNotOverfilled(order, newFilledQuantity);

        OrderStatus nextStatus = newFilledQuantity.compareTo(order.quantity()) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;

        Instant filledAt = Instant.now(clock);
        return orderRepository.save(new Order(
                order.orderId(),
                order.intentId(),
                order.portfolioId(),
                order.instrumentId(),
                order.side(),
                order.orderType(),
                order.quantity(),
                newFilledQuantity,
                order.limitPrice(),
                order.timeInForce(),
                nextStatus,
                order.createdAt(),
                filledAt
        ));
    }

    private void validateFillable(Order order) {
        if (order.status() != OrderStatus.ACKED
                && order.status() != OrderStatus.PARTIALLY_FILLED
                && order.status() != OrderStatus.CANCEL_REQUESTED) {
            throw new OrderFillException("only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled");
        }
    }

    private void validateFillQuantity(BigDecimal fillQuantity) {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderFillException("fillQuantity must be greater than zero");
        }
    }

    private void validateNotOverfilled(Order order, BigDecimal newFilledQuantity) {
        if (newFilledQuantity.compareTo(order.quantity()) > 0) {
            /**
             * TODO: 이후 DB/영속화가 들어오면 fill event 저장과 order 상태 갱신을 하나의 transaction으로 묶음
             */
            throw new OrderFillException("filled quantity exceeds order quantity");
        }
    }
}
