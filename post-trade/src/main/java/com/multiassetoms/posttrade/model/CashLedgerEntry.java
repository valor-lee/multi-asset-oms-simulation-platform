package com.multiassetoms.posttrade.model;

import com.multiassetoms.intentgeneration.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashLedgerEntry(
        UUID entryId,
        UUID tradeId,
        String portfolioId,
        OrderSide side,
        BigDecimal cashDelta,
        Instant postedAt
) {
}
