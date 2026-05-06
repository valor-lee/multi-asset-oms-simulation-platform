package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskCheckContext(
        PreTradeRiskLimitContext limitContext,
        PreTradeRiskExposureContext exposureContext,
        PreTradeRiskOpenOrderContext openOrderContext
) {

    public PreTradeRiskCheckContext(
            PreTradeRiskLimitContext limitContext,
            PreTradeRiskExposureContext exposureContext
    ) {
        this(limitContext, exposureContext, PreTradeRiskOpenOrderContext.empty());
    }

    public PreTradeRiskCheckContext {
        if (limitContext == null) {
            limitContext = PreTradeRiskLimitContext.empty();
        }
        if (exposureContext == null) {
            exposureContext = PreTradeRiskExposureContext.empty();
        }
        if (openOrderContext == null) {
            openOrderContext = PreTradeRiskOpenOrderContext.empty();
        }
    }

    public static PreTradeRiskCheckContext empty() {
        return new PreTradeRiskCheckContext(
                PreTradeRiskLimitContext.empty(),
                PreTradeRiskExposureContext.empty(),
                PreTradeRiskOpenOrderContext.empty()
        );
    }
}
