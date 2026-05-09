package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

import java.math.BigDecimal;

public class PositiveQuantityRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        if (command.requestedQty() == null || command.requestedQty().compareTo(BigDecimal.ZERO) <= 0) {
            return failed(
                    PreTradeRiskRuleCode.POSITIVE_QUANTITY,
                    "requestedQty must be greater than zero",
                    valueOf(command.requestedQty()),
                    "0"
            );
        }
        return passed(
                PreTradeRiskRuleCode.POSITIVE_QUANTITY,
                "requestedQty is greater than zero",
                valueOf(command.requestedQty()),
                "0"
        );
    }
}
