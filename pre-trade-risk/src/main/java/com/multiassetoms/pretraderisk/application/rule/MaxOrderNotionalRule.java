package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

import java.math.BigDecimal;

public class MaxOrderNotionalRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskLimitContext limitContext = checkContext.limitContext();
        if (limitContext.maxOrderNotional() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL,
                    "maxOrderNotional limit is not configured",
                    orderNotionalValue(command),
                    null
            );
        }
        if (command.requestedQty() == null || command.limitPrice() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL,
                    "order notional cannot be calculated",
                    orderNotionalValue(command),
                    valueOf(limitContext.maxOrderNotional())
            );
        }

        BigDecimal orderNotional = command.requestedQty().multiply(command.limitPrice());
        if (orderNotional.compareTo(limitContext.maxOrderNotional()) > 0) {
            return failed(
                    PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL,
                    "order notional exceeds maxOrderNotional",
                    valueOf(orderNotional),
                    valueOf(limitContext.maxOrderNotional())
            );
        }
        return passed(
                PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL,
                "order notional is within maxOrderNotional",
                valueOf(orderNotional),
                valueOf(limitContext.maxOrderNotional())
        );
    }

    private String orderNotionalValue(PreTradeRiskCheckCommand command) {
        if (command.requestedQty() == null || command.limitPrice() == null) {
            return null;
        }
        return valueOf(command.requestedQty().multiply(command.limitPrice()));
    }
}
