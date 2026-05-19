package com.multiassetoms.execution.model;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(
        UUID orderId,
        UUID intentId,
        String portfolioId,
        String instrumentId,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
