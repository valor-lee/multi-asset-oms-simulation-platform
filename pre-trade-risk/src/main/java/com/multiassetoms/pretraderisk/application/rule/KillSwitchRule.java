package com.multiassetoms.pretraderisk.application.rule;

import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskControlContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;

public class KillSwitchRule extends AbstractPreTradeRiskRule {

    @Override
    public PreTradeRiskRuleCheckResult evaluate(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskControlContext controlContext = checkContext.controlContext();
        if (controlContext.killSwitchEnabled() == null) {
            return skipped(
                    PreTradeRiskRuleCode.KILL_SWITCH,
                    "kill switch context is not configured",
                    null,
                    "false"
            );
        }
        if (controlContext.killSwitchEnabled()) {
            return failed(
                    PreTradeRiskRuleCode.KILL_SWITCH,
                    "kill switch is enabled",
                    valueOf(controlContext.killSwitchEnabled()),
                    "false"
            );
        }
        return passed(
                PreTradeRiskRuleCode.KILL_SWITCH,
                "kill switch is disabled",
                valueOf(controlContext.killSwitchEnabled()),
                "false"
        );
    }
}
