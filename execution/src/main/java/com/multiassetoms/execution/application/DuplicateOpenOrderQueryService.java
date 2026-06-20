package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.ExecutionRequestException;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

@Service
public class DuplicateOpenOrderQueryService {

    private static final EnumSet<OrderStatus> OPEN_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.SENT,
            OrderStatus.ACKED,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.CANCEL_REQUESTED
    );

    private final OrderRepository orderRepository;

    public DuplicateOpenOrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * pre-trade risk duplicate open order context에 사용할 matching open order를 조회한다.
     */
    public DuplicateOpenOrderResult findDuplicateOpenOrder(
            String portfolioId,
            String instrumentId,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            TimeInForce timeInForce,
            UUID excludeIntentId
    ) {
        validateRequired(portfolioId, instrumentId, side, orderType, quantity, limitPrice, timeInForce);

        return orderRepository.findAll().stream()
                .filter(order -> !Objects.equals(order.intentId(), excludeIntentId))
                .filter(this::isOpen)
                .filter(order -> matches(order, portfolioId, instrumentId, side, orderType, quantity, limitPrice, timeInForce))
                .findFirst()
                .map(DuplicateOpenOrderResult::found)
                .orElseGet(DuplicateOpenOrderResult::notFound);
    }

    private void validateRequired(
            String portfolioId,
            String instrumentId,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            TimeInForce timeInForce
    ) {
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new ExecutionRequestException("portfolioId is required");
        }
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new ExecutionRequestException("instrumentId is required");
        }
        if (side == null) {
            throw new ExecutionRequestException("side is required");
        }
        if (orderType == null) {
            throw new ExecutionRequestException("orderType is required");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExecutionRequestException("quantity must be greater than zero");
        }
        if (timeInForce == null) {
            throw new ExecutionRequestException("timeInForce is required");
        }
        if (orderType == OrderType.LIMIT && limitPrice == null) {
            throw new ExecutionRequestException("limitPrice is required for LIMIT orders");
        }
    }

    private boolean isOpen(Order order) {
        return OPEN_STATUSES.contains(order.status());
    }

    private boolean matches(
            Order order,
            String portfolioId,
            String instrumentId,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            TimeInForce timeInForce
    ) {
        return Objects.equals(order.portfolioId(), portfolioId)
                && Objects.equals(order.instrumentId(), instrumentId)
                && order.side() == side
                && order.orderType() == orderType
                && sameAmount(order.quantity(), quantity)
                && sameNullableAmount(order.limitPrice(), limitPrice)
                && order.timeInForce() == timeInForce;
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private boolean sameNullableAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
