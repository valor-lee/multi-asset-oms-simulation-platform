package com.multiassetoms.pretraderisk.model;

import java.math.BigDecimal;

public record PreTradeRiskExposureContext(
        BigDecimal currentPositionQty
) {

    public static PreTradeRiskExposureContext empty() {
        return new PreTradeRiskExposureContext(null);
    }
}
