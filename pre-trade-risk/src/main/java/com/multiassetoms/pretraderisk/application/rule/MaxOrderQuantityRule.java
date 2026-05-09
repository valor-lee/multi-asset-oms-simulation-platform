package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

public class MaxOrderQuantityRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskLimitContext limitContext = checkContext.limitContext();
        if (limitContext.maxOrderQty() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_ORDER_QUANTITY,
                    "maxOrderQty limit is not configured",
                    valueOf(command.requestedQty()),
                    null
            );
        }
        if (command.requestedQty() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_ORDER_QUANTITY,
                    "requestedQty is not present",
                    null,
                    valueOf(limitContext.maxOrderQty())
            );
        }
        if (command.requestedQty().compareTo(limitContext.maxOrderQty()) > 0) {
            return failed(
                    PreTradeRiskRuleCode.MAX_ORDER_QUANTITY,
                    "requestedQty exceeds maxOrderQty",
                    valueOf(command.requestedQty()),
                    valueOf(limitContext.maxOrderQty())
            );
        }
        return passed(
                PreTradeRiskRuleCode.MAX_ORDER_QUANTITY,
                "requestedQty is within maxOrderQty",
                valueOf(command.requestedQty()),
                valueOf(limitContext.maxOrderQty())
        );
    }
}
