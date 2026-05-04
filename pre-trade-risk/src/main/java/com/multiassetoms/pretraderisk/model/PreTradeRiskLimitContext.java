package com.multiassetoms.pretraderisk.model;

import java.math.BigDecimal;

public record PreTradeRiskLimitContext(
        BigDecimal maxOrderQty
) {

    public static PreTradeRiskLimitContext empty() {
        return new PreTradeRiskLimitContext(null);
    }
}
