package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskControlContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskMarketContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOpenOrderContext;

import java.math.BigDecimal;
import java.util.UUID;

public record PreTradeRiskEvaluationRequest(
        BigDecimal maxOrderQty,
        BigDecimal maxOrderNotional,
        BigDecimal maxPositionQty,
        BigDecimal currentPositionQty,
        Boolean duplicateOpenOrderExists,
        UUID duplicateOpenOrderId,
        BigDecimal lowerPriceBand,
        BigDecimal upperPriceBand,
        Boolean killSwitchEnabled
) {

    public PreTradeRiskCheckContext toCheckContext() {
        return new PreTradeRiskCheckContext(
                new PreTradeRiskLimitContext(maxOrderQty, maxOrderNotional, maxPositionQty),
                new PreTradeRiskExposureContext(currentPositionQty),
                new PreTradeRiskOpenOrderContext(duplicateOpenOrderExists, duplicateOpenOrderId),
                new PreTradeRiskMarketContext(lowerPriceBand, upperPriceBand),
                new PreTradeRiskControlContext(killSwitchEnabled)
        );
    }
}
