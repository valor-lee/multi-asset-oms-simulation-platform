package com.multiassetoms.posttrade.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CurrentAverageCost(
        String portfolioId,
        String instrumentId,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal averageCost,
        Instant updatedAt
) {

    public static CurrentAverageCost empty(String portfolioId, String instrumentId) {
        return new CurrentAverageCost(
                portfolioId,
                instrumentId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
    }
}
