package com.multiassetoms.intentgeneration.manual;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;

import java.math.BigDecimal;

public record ManualOrderIntentRequest(
        String portfolioId,
        String instrumentId,
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
