package com.multiassetoms.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPrice(
        String instrumentId,
        BigDecimal price,
        Instant observedAt,
        Instant updatedAt
) {
}
