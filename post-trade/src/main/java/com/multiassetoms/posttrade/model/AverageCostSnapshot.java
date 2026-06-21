package com.multiassetoms.posttrade.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AverageCostSnapshot(
        String portfolioId,
        String instrumentId,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal averageCost,
        Instant updatedAt
) {

    public static AverageCostSnapshot empty(String portfolioId, String instrumentId) {
        return new AverageCostSnapshot(
                portfolioId,
                instrumentId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
    }
}
