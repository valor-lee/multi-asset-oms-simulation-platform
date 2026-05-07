package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskCheckContext(
        PreTradeRiskLimitContext limitContext,
        PreTradeRiskExposureContext exposureContext,
        PreTradeRiskOpenOrderContext openOrderContext,
        PreTradeRiskMarketContext marketContext,
        PreTradeRiskControlContext controlContext
) {

    public PreTradeRiskCheckContext(
            PreTradeRiskLimitContext limitContext,
            PreTradeRiskExposureContext exposureContext
    ) {
        this(limitContext, exposureContext, PreTradeRiskOpenOrderContext.empty());
    }

    public PreTradeRiskCheckContext(
            PreTradeRiskLimitContext limitContext,
            PreTradeRiskExposureContext exposureContext,
            PreTradeRiskOpenOrderContext openOrderContext
    ) {
        this(
                limitContext,
                exposureContext,
                openOrderContext,
                PreTradeRiskMarketContext.empty(),
                PreTradeRiskControlContext.empty()
        );
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
        if (marketContext == null) {
            marketContext = PreTradeRiskMarketContext.empty();
        }
        if (controlContext == null) {
            controlContext = PreTradeRiskControlContext.empty();
        }
    }

    public static PreTradeRiskCheckContext empty() {
        return new PreTradeRiskCheckContext(
                PreTradeRiskLimitContext.empty(),
                PreTradeRiskExposureContext.empty(),
                PreTradeRiskOpenOrderContext.empty(),
                PreTradeRiskMarketContext.empty(),
                PreTradeRiskControlContext.empty()
        );
    }
}
