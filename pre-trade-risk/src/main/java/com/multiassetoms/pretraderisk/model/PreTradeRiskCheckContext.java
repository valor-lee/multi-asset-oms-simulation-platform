package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskCheckContext(
        PreTradeRiskLimitContext limitContext,
        PreTradeRiskExposureContext exposureContext
) {

    public PreTradeRiskCheckContext {
        if (limitContext == null) {
            limitContext = PreTradeRiskLimitContext.empty();
        }
        if (exposureContext == null) {
            exposureContext = PreTradeRiskExposureContext.empty();
        }
    }

    public static PreTradeRiskCheckContext empty() {
        return new PreTradeRiskCheckContext(
                PreTradeRiskLimitContext.empty(),
                PreTradeRiskExposureContext.empty()
        );
    }
}
