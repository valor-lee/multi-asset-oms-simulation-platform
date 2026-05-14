package com.multiassetoms.pretraderisk.model;

import com.multiassetoms.intentgeneration.model.OrderIntent;

public record PreTradeRiskOrderIntentResult(
        OrderIntent intent,
        PreTradeRiskCheckResult riskCheckResult
) {
}
