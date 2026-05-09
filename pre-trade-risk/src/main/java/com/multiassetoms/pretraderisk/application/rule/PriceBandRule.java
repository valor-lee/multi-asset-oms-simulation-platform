package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskMarketContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

public class PriceBandRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskMarketContext marketContext = checkContext.marketContext();
        if (marketContext.lowerPriceBand() == null || marketContext.upperPriceBand() == null) {
            return skipped(
                    PreTradeRiskRuleCode.PRICE_BAND,
                    "price band context is not configured",
                    valueOf(command.limitPrice()),
                    priceBandValue(marketContext)
            );
        }
        if (command.limitPrice() == null) {
            return skipped(
                    PreTradeRiskRuleCode.PRICE_BAND,
                    "limitPrice is not present",
                    null,
                    priceBandValue(marketContext)
            );
        }
        if (command.limitPrice().compareTo(marketContext.lowerPriceBand()) < 0
                || command.limitPrice().compareTo(marketContext.upperPriceBand()) > 0) {
            return failed(
                    PreTradeRiskRuleCode.PRICE_BAND,
                    "limitPrice is outside price band",
                    valueOf(command.limitPrice()),
                    priceBandValue(marketContext)
            );
        }
        return passed(
                PreTradeRiskRuleCode.PRICE_BAND,
                "limitPrice is within price band",
                valueOf(command.limitPrice()),
                priceBandValue(marketContext)
        );
    }

    private String priceBandValue(PreTradeRiskMarketContext marketContext) {
        if (marketContext.lowerPriceBand() == null || marketContext.upperPriceBand() == null) {
            return null;
        }
        return marketContext.lowerPriceBand() + ".." + marketContext.upperPriceBand();
    }
}
