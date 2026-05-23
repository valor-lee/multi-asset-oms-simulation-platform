package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.OrderFillExecutionRepository;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillExecution;
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
    private final OrderFillExecutionRepository fillExecutionRepository;
    private final Clock clock;

    public OrderFillService(
            OrderRepository orderRepository,
            OrderFillExecutionRepository fillExecutionRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.fillExecutionRepository = fillExecutionRepository;
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
        return fill(orderId, UUID.randomUUID(), fillQuantity, null, null);
    }

    /**
     * broker/exchange 체결 이벤트를 idempotent하게 order에 반영한다.
     * 상태별 처리:
     * - ACKED: 첫 체결을 반영해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - PARTIALLY_FILLED: 추가 체결을 누적해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - CANCEL_REQUESTED: cancel-fill race condition을 허용해 추가 체결을 반영
     * - 이미 처리한 fillExecutionId: 중복 이벤트로 보고 수량을 다시 누적하지 않음
     * - 그 외 상태: 체결 반영 대상이 아니므로 예외
     *
     * @param orderId 체결을 반영할 order id
     * @param fillExecutionId broker/exchange 체결 이벤트의 고유 id
     * @param fillQuantity 이번에 추가로 체결된 수량
     * @return 체결 수량과 상태가 갱신된 order
     */
    public Order fill(UUID orderId, UUID fillExecutionId, BigDecimal fillQuantity) {
        return fill(orderId, fillExecutionId, fillQuantity, null, null);
    }

    /**
     * broker/exchange 체결 이벤트를 가격과 함께 idempotent하게 order에 반영한다.
     * 상태별 처리:
     * - ACKED: 첫 체결을 반영해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - PARTIALLY_FILLED: 추가 체결을 누적해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - CANCEL_REQUESTED: cancel-fill race condition을 허용해 추가 체결을 반영
     * - 이미 처리한 fillExecutionId: 중복 이벤트로 보고 수량을 다시 누적하지 않음
     * - 그 외 상태: 체결 반영 대상이 아니므로 예외
     *
     * @param orderId 체결을 반영할 order id
     * @param fillExecutionId broker/exchange 체결 이벤트의 고유 id
     * @param fillQuantity 이번에 추가로 체결된 수량
     * @param fillPrice 이번 체결 가격
     * @return 체결 수량과 상태가 갱신된 order
     */
    public Order fill(
            UUID orderId,
            UUID fillExecutionId,
            BigDecimal fillQuantity,
            BigDecimal fillPrice
    ) {
        return fill(orderId, fillExecutionId, fillQuantity, fillPrice, null);
    }

    /**
     * broker/exchange 체결 이벤트를 가격과 수수료와 함께 idempotent하게 order에 반영한다.
     * 상태별 처리:
     * - ACKED: 첫 체결을 반영해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - PARTIALLY_FILLED: 추가 체결을 누적해 PARTIALLY_FILLED 또는 FILLED로 전이
     * - CANCEL_REQUESTED: cancel-fill race condition을 허용해 추가 체결을 반영
     * - 이미 처리한 fillExecutionId: 중복 이벤트로 보고 수량을 다시 누적하지 않음
     * - 그 외 상태: 체결 반영 대상이 아니므로 예외
     *
     * @param orderId 체결을 반영할 order id
     * @param fillExecutionId broker/exchange 체결 이벤트의 고유 id
     * @param fillQuantity 이번에 추가로 체결된 수량
     * @param fillPrice 이번 체결 가격
     * @param feeAmount 이번 체결 수수료
     * @return 체결 수량과 상태가 갱신된 order
     */
    public Order fill(
            UUID orderId,
            UUID fillExecutionId,
            BigDecimal fillQuantity,
            BigDecimal fillPrice,
            BigDecimal feeAmount
    ) {
        validateFillExecutionId(fillExecutionId);
        OrderFillExecution existingFillExecution =
                fillExecutionRepository.findByFillExecutionId(fillExecutionId).orElse(null);

        if (existingFillExecution != null) {
            return duplicateFillResult(orderId, existingFillExecution);
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderFillException("order not found"));

        validateFillable(order);
        validateFillQuantity(fillQuantity);
        validateFillPrice(fillPrice);
        validateFeeAmount(feeAmount);

        BigDecimal newFilledQuantity = order.filledQuantity().add(fillQuantity);
        validateNotOverfilled(order, newFilledQuantity);

        OrderStatus nextStatus = newFilledQuantity.compareTo(order.quantity()) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;

        Instant filledAt = Instant.now(clock);
        Order filledOrder = orderRepository.save(new Order(
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

        fillExecutionRepository.save(new OrderFillExecution(
                fillExecutionId,
                orderId,
                fillQuantity,
                fillPrice,
                feeAmount,
                filledAt
        ));
        return filledOrder;
    }

    private void validateFillExecutionId(UUID fillExecutionId) {
        if (fillExecutionId == null) {
            throw new OrderFillException("fillExecutionId is required");
        }
    }

    private Order duplicateFillResult(UUID orderId, OrderFillExecution existingFillExecution) {
        if (!existingFillExecution.orderId().equals(orderId)) {
            throw new OrderFillException("fillExecutionId belongs to another order");
        }
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderFillException("order not found"));
    }

    private void validateFillable(Order order) {
        if (order.status() != OrderStatus.ACKED
                && order.status() != OrderStatus.PARTIALLY_FILLED
                && order.status() != OrderStatus.CANCEL_REQUESTED) {
            throw new OrderFillException(
                    "only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled"
            );
        }
    }

    private void validateFillQuantity(BigDecimal fillQuantity) {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderFillException("fillQuantity must be greater than zero");
        }
    }

    private void validateFillPrice(BigDecimal fillPrice) {
        if (fillPrice != null && fillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderFillException("fillPrice must be greater than zero");
        }
    }

    private void validateFeeAmount(BigDecimal feeAmount) {
        if (feeAmount != null && feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderFillException("feeAmount must be zero or greater");
        }
    }

    private void validateNotOverfilled(Order order, BigDecimal newFilledQuantity) {
        if (newFilledQuantity.compareTo(order.quantity()) > 0) {
            // TODO: 이후 DB/영속화가 들어오면 fill event 저장과 order 상태 갱신을
            // 하나의 transaction으로 묶음.
            throw new OrderFillException("filled quantity exceeds order quantity");
        }
    }
}
