package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.pretraderisk.application.rule.DuplicateOpenOrderRule;
import com.multiassetoms.pretraderisk.application.rule.KillSwitchRule;
import com.multiassetoms.pretraderisk.application.rule.LimitPriceRequiredRule;
import com.multiassetoms.pretraderisk.application.rule.MaxOrderNotionalRule;
import com.multiassetoms.pretraderisk.application.rule.MaxOrderQuantityRule;
import com.multiassetoms.pretraderisk.application.rule.MaxPositionQuantityRule;
import com.multiassetoms.pretraderisk.application.rule.PriceBandRule;
import com.multiassetoms.pretraderisk.application.rule.PositiveLimitPriceRule;
import com.multiassetoms.pretraderisk.application.rule.PositiveQuantityRule;
import com.multiassetoms.pretraderisk.application.rule.PreTradeRiskRule;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PreTradeRiskCheckService {

    private final List<PreTradeRiskRule> rules;
    private final Clock clock;

    public PreTradeRiskCheckService() {
        this(defaultRules(), Clock.systemUTC());
    }

    PreTradeRiskCheckService(Clock clock) {
        this(defaultRules(), clock);
    }

    PreTradeRiskCheckService(List<PreTradeRiskRule> rules, Clock clock) {
        this.rules = List.copyOf(rules);
        this.clock = clock;
    }

    public PreTradeRiskCheckResult evaluateBasicRules(PreTradeRiskCheckCommand command) {
        return evaluateWithContext(command, PreTradeRiskCheckContext.empty());
    }

    public PreTradeRiskCheckResult evaluateWithLimits(
            PreTradeRiskCheckCommand command,
            PreTradeRiskLimitContext limitContext
    ) {
        return evaluateWithContext(command, new PreTradeRiskCheckContext(
                limitContext,
                PreTradeRiskExposureContext.empty()
        ));
    }

    public PreTradeRiskCheckResult evaluateWithContext(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskCheckContext resolvedContext = checkContext == null
                ? PreTradeRiskCheckContext.empty()
                : checkContext;

        List<PreTradeRiskRuleCheckResult> ruleResults = rules.stream()
                .map(rule -> rule.evaluate(command, resolvedContext))
                .toList();

        List<PreTradeRiskRuleCheckResult> failedResults = ruleResults.stream()
                .filter(result -> result.status() == PreTradeRiskRuleStatus.FAILED)
                .toList();
        if (!failedResults.isEmpty()) {
            return reject(command, failedResults.getFirst().message(), ruleResults);
        }
        return approve(command, ruleResults);
    }

    private PreTradeRiskCheckResult approve(
            PreTradeRiskCheckCommand command,
            List<PreTradeRiskRuleCheckResult> ruleResults
    ) {
        return new PreTradeRiskCheckResult(
                command.intentId(),
                PreTradeRiskDecision.APPROVED,
                "approved",
                Instant.now(clock),
                List.copyOf(ruleResults)
        );
    }

    private PreTradeRiskCheckResult reject(
            PreTradeRiskCheckCommand command,
            String reason,
            List<PreTradeRiskRuleCheckResult> ruleResults
    ) {
        return new PreTradeRiskCheckResult(
                command.intentId(),
                PreTradeRiskDecision.REJECTED,
                reason,
                Instant.now(clock),
                List.copyOf(ruleResults)
        );
    }

    private static List<PreTradeRiskRule> defaultRules() {
        return List.of(
                new PositiveQuantityRule(),
                new LimitPriceRequiredRule(),
                new PositiveLimitPriceRule(),
                new MaxOrderQuantityRule(),
                new MaxOrderNotionalRule(),
                new MaxPositionQuantityRule(),
                new DuplicateOpenOrderRule(),
                new PriceBandRule(),
                new KillSwitchRule()
        );
    }
}
