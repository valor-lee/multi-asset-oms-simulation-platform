package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleStatus;

abstract class AbstractPreTradeRiskRule implements PreTradeRiskRule {

    protected PreTradeRiskRuleCheckResult passed(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.PASSED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    protected PreTradeRiskRuleCheckResult failed(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.FAILED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    protected PreTradeRiskRuleCheckResult skipped(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.SKIPPED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    protected String valueOf(Object value) {
        return value == null ? null : value.toString();
    }
}
