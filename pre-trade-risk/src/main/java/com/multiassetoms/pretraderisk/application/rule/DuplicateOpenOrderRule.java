package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOpenOrderContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

public class DuplicateOpenOrderRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskOpenOrderContext openOrderContext = checkContext.openOrderContext();
        if (openOrderContext.duplicateOpenOrderExists() == null) {
            return skipped(
                    PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER,
                    "duplicate open order context is not configured",
                    null,
                    "false"
            );
        }
        if (openOrderContext.duplicateOpenOrderExists()) {
            return failed(
                    PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER,
                    "duplicate open order exists",
                    evaluatedValue(openOrderContext),
                    "false"
            );
        }
        return passed(
                PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER,
                "duplicate open order does not exist",
                valueOf(openOrderContext.duplicateOpenOrderExists()),
                "false"
        );
    }

    private String evaluatedValue(PreTradeRiskOpenOrderContext openOrderContext) {
        if (openOrderContext.duplicateOpenOrderId() != null) {
            return openOrderContext.duplicateOpenOrderId().toString();
        }
        return valueOf(openOrderContext.duplicateOpenOrderExists());
    }
}
