package com.multiassetoms.posttrade.model;

import java.math.BigDecimal;
import java.time.Instant;

public record UnrealizedPnlSnapshot(
        String portfolioId,
        String instrumentId,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal marketPrice,
        BigDecimal costBasis,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        Instant valuedAt
) {
}
