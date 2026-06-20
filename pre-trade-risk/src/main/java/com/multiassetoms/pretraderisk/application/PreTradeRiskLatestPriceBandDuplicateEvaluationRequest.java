package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskControlContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskMarketContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOpenOrderContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRequestException;

import java.math.BigDecimal;

public record PreTradeRiskLatestPriceBandDuplicateEvaluationRequest(
        BigDecimal maxOrderQty,
        BigDecimal maxOrderNotional,
        BigDecimal maxPositionQty,
        BigDecimal currentPositionQty,
        Boolean killSwitchEnabled,
        BigDecimal priceBandRate
) {

    public PreTradeRiskCheckContext toBaseCheckContext() {
        return new PreTradeRiskCheckContext(
                new PreTradeRiskLimitContext(maxOrderQty, maxOrderNotional, maxPositionQty),
                new PreTradeRiskExposureContext(currentPositionQty),
                PreTradeRiskOpenOrderContext.empty(),
                PreTradeRiskMarketContext.empty(),
                new PreTradeRiskControlContext(killSwitchEnabled)
        );
    }

    public BigDecimal requirePriceBandRate() {
        if (priceBandRate == null) {
            throw new PreTradeRiskRequestException("priceBandRate is required");
        }
        if (priceBandRate.compareTo(BigDecimal.ZERO) < 0
                || priceBandRate.compareTo(BigDecimal.ONE) > 0) {
            throw new PreTradeRiskRequestException("priceBandRate must be between 0 and 1");
        }
        return priceBandRate;
    }
}
