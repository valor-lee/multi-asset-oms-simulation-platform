package com.multiassetoms.intentgeneration.rebalancing;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;

import java.math.BigDecimal;

public record RebalancingOrderIntentRequest(
        String portfolioId,
        String instrumentId,
        String rebalanceRunId,
        OrderSide side,
        OrderType orderType,
        BigDecimal requestedQty,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        String reason,
        String idempotencyKey,
        String createdBy
) {
}
