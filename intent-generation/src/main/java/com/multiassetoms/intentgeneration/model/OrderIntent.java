package com.multiassetoms.intentgeneration.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderIntent(
        UUID intentId,
        String portfolioId,
        String instrumentId,
        OrderIntentSourceType sourceType,
        String sourceRefId,
        OrderSide side,
        OrderType orderType,
        BigDecimal requestedQty,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        String reason,
        OrderIntentStatus status,
        String idempotencyKey,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
