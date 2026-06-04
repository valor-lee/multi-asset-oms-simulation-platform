package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreTradeRiskEvaluationRequestTest {

    @Test
    void convertsRequestToCheckContext() {
        UUID duplicateOpenOrderId = UUID.fromString("00000000-0000-0000-0000-000000028001");

        PreTradeRiskEvaluationRequest request = new PreTradeRiskEvaluationRequest(
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("90"),
                true,
                duplicateOpenOrderId,
                new BigDecimal("50000"),
                new BigDecimal("60000"),
                false
        );

        PreTradeRiskCheckContext context = request.toCheckContext();

        assertEquals(new BigDecimal("10"), context.limitContext().maxOrderQty());
        assertEquals(new BigDecimal("550000"), context.limitContext().maxOrderNotional());
        assertEquals(new BigDecimal("100"), context.limitContext().maxPositionQty());
        assertEquals(new BigDecimal("90"), context.exposureContext().currentPositionQty());
        assertEquals(true, context.openOrderContext().duplicateOpenOrderExists());
        assertEquals(duplicateOpenOrderId, context.openOrderContext().duplicateOpenOrderId());
        assertEquals(new BigDecimal("50000"), context.marketContext().lowerPriceBand());
        assertEquals(new BigDecimal("60000"), context.marketContext().upperPriceBand());
        assertEquals(false, context.controlContext().killSwitchEnabled());
    }
}
