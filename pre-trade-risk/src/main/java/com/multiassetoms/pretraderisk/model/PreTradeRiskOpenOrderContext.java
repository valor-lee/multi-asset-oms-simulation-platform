package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskOpenOrderContext(
        Boolean duplicateOpenOrderExists
) {

    public static PreTradeRiskOpenOrderContext empty() {
        return new PreTradeRiskOpenOrderContext(null);
    }
}
