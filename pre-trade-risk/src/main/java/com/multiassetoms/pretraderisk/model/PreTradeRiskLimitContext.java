package com.multiassetoms.pretraderisk.model;

import java.math.BigDecimal;

public record PreTradeRiskLimitContext(
        BigDecimal maxOrderQty,
        BigDecimal maxOrderNotional,
        BigDecimal maxPositionQty
) {

    public PreTradeRiskLimitContext(BigDecimal maxOrderQty) {
        this(maxOrderQty, null);
    }

    public PreTradeRiskLimitContext(BigDecimal maxOrderQty, BigDecimal maxOrderNotional) {
        this(maxOrderQty, maxOrderNotional, null);
    }

    public static PreTradeRiskLimitContext empty() {
        return new PreTradeRiskLimitContext(null, null, null);
    }
}
