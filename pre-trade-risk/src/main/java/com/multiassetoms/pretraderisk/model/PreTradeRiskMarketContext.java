package com.multiassetoms.pretraderisk.model;

import java.math.BigDecimal;

public record PreTradeRiskMarketContext(
        BigDecimal lowerPriceBand,
        BigDecimal upperPriceBand
) {

    public static PreTradeRiskMarketContext empty() {
        return new PreTradeRiskMarketContext(null, null);
    }
}
