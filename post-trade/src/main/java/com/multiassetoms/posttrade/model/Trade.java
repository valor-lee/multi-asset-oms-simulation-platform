package com.multiassetoms.posttrade.model;

import com.multiassetoms.intentgeneration.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Trade(
        UUID tradeId,
        UUID orderId,
        UUID intentId,
        String portfolioId,
        String instrumentId,
        OrderSide side,
        BigDecimal quantity,
        TradeStatus status,
        Instant capturedAt,
        Instant updatedAt
) {
}
