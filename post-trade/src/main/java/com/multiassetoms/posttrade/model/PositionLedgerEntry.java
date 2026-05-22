package com.multiassetoms.posttrade.model;

import com.multiassetoms.intentgeneration.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionLedgerEntry(
        UUID entryId,
        UUID tradeId,
        String portfolioId,
        String instrumentId,
        OrderSide side,
        BigDecimal quantityDelta,
        Instant postedAt
) {
}
