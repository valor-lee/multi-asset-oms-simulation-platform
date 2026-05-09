package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

public class LimitPriceRequiredRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        if (command.orderType() == OrderType.LIMIT && command.limitPrice() == null) {
            return failed(
                    PreTradeRiskRuleCode.LIMIT_PRICE_REQUIRED,
                    "limitPrice is required for LIMIT orders",
                    null,
                    "required"
            );
        }
        if (command.orderType() != OrderType.LIMIT) {
            return skipped(
                    PreTradeRiskRuleCode.LIMIT_PRICE_REQUIRED,
                    "orderType is not LIMIT",
                    valueOf(command.orderType()),
                    "LIMIT"
            );
        }
        return passed(
                PreTradeRiskRuleCode.LIMIT_PRICE_REQUIRED,
                "limitPrice is present for LIMIT order",
                valueOf(command.limitPrice()),
                "required"
        );
    }
}
