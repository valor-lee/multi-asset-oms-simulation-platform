package com.multiassetoms.intentgeneration.strategy;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;

import java.math.BigDecimal;

public record StrategyOrderIntentRequest(
        String portfolioId,
        String instrumentId,
        String strategySignalId,
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
