package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

import java.math.BigDecimal;

public class MaxPositionQuantityRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskLimitContext limitContext = checkContext.limitContext();
        PreTradeRiskExposureContext exposureContext = checkContext.exposureContext();
        if (limitContext.maxPositionQty() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                    "maxPositionQty limit is not configured",
                    expectedPositionValue(command, exposureContext),
                    null
            );
        }
        if (exposureContext.currentPositionQty() == null || command.requestedQty() == null || command.side() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                    "expected position cannot be calculated",
                    expectedPositionValue(command, exposureContext),
                    valueOf(limitContext.maxPositionQty())
            );
        }

        BigDecimal expectedPositionQty = expectedPositionQty(command, exposureContext);
        if (expectedPositionQty.abs().compareTo(limitContext.maxPositionQty()) > 0) {
            return failed(
                    PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                    "expected position exceeds maxPositionQty",
                    valueOf(expectedPositionQty),
                    valueOf(limitContext.maxPositionQty())
            );
        }
        return passed(
                PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                "expected position is within maxPositionQty",
                valueOf(expectedPositionQty),
                valueOf(limitContext.maxPositionQty())
        );
    }

    private String expectedPositionValue(
            PreTradeRiskCheckCommand command,
            PreTradeRiskExposureContext exposureContext
    ) {
        if (exposureContext.currentPositionQty() == null || command.requestedQty() == null || command.side() == null) {
            return null;
        }
        return valueOf(expectedPositionQty(command, exposureContext));
    }

    private BigDecimal expectedPositionQty(
            PreTradeRiskCheckCommand command,
            PreTradeRiskExposureContext exposureContext
    ) {
        if (command.side() == OrderSide.SELL) {
            return exposureContext.currentPositionQty().subtract(command.requestedQty());
        }
        return exposureContext.currentPositionQty().add(command.requestedQty());
    }
}
