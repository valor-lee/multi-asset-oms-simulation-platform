package com.multiassetoms.pretraderisk.model;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;

import java.math.BigDecimal;
import java.util.UUID;

public record PreTradeRiskCheckCommand(
        UUID intentId,
        String portfolioId,
        String instrumentId,
        OrderSide side,
        OrderType orderType,
        BigDecimal requestedQty,
        BigDecimal limitPrice
) {

    public static PreTradeRiskCheckCommand from(OrderIntent intent) {
        return new PreTradeRiskCheckCommand(
                intent.intentId(),
                intent.portfolioId(),
                intent.instrumentId(),
                intent.side(),
                intent.orderType(),
                intent.requestedQty(),
                intent.limitPrice()
        );
    }
}
