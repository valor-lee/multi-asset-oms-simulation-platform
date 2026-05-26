package com.multiassetoms.posttrade.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RealizedPnlEntry(
        UUID entryId,
        UUID tradeId,
        String portfolioId,
        String instrumentId,
        BigDecimal quantity,
        BigDecimal averageSellPrice,
        BigDecimal averageCost,
        BigDecimal grossNotional,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        BigDecimal realizedPnl,
        Instant postedAt
) {
}
