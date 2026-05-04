package com.multiassetoms.pretraderisk.model;

public record PreTradeRiskRuleCheckResult(
        PreTradeRiskRuleCode ruleCode,
        PreTradeRiskRuleStatus status,
        String message,
        String evaluatedValue,
        String thresholdValue
) {
}
