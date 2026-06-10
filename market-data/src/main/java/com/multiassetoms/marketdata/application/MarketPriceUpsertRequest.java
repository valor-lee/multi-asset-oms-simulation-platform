package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.model.MarketDataException;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceUpsertRequest(
        BigDecimal price,
        Instant observedAt
) {

    public BigDecimal requirePrice() {
        if (price == null) {
            throw new MarketDataException("price is required");
        }
        return price;
    }
}
