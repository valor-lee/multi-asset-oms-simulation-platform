package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

import java.math.BigDecimal;

public class PositiveLimitPriceRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        if (command.limitPrice() == null) {
            return skipped(
                    PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE,
                    "limitPrice is not present",
                    null,
                    "0"
            );
        }
        if (command.limitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return failed(
                    PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE,
                    "limitPrice must be greater than zero",
                    valueOf(command.limitPrice()),
                    "0"
            );
        }
        return passed(
                PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE,
                "limitPrice is greater than zero",
                valueOf(command.limitPrice()),
                "0"
        );
    }
}
