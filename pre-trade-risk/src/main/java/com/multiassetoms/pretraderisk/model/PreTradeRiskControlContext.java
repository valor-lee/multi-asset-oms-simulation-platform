package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskControlContext(
        Boolean killSwitchEnabled
) {

    public static PreTradeRiskControlContext empty() {
        return new PreTradeRiskControlContext(null);
    }
}
