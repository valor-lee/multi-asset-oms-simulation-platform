package com.multiassetoms.pretraderisk.model;

import java.math.BigDecimal;

public record PreTradeRiskLimitContext(
        BigDecimal maxOrderQty,
        BigDecimal maxOrderNotional
) {

    public PreTradeRiskLimitContext(BigDecimal maxOrderQty) {
        this(maxOrderQty, null);
    }

    public static PreTradeRiskLimitContext empty() {
        return new PreTradeRiskLimitContext(null, null);
    }
}
