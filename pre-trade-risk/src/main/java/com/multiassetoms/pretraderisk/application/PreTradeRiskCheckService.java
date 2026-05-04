package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PreTradeRiskCheckService {

    private final Clock clock;

    public PreTradeRiskCheckService() {
        this(Clock.systemUTC());
    }

    PreTradeRiskCheckService(Clock clock) {
        this.clock = clock;
    }

    public PreTradeRiskCheckResult check(PreTradeRiskCheckCommand command) {
        return check(command, PreTradeRiskLimitContext.empty());
    }

    public PreTradeRiskCheckResult check(
            PreTradeRiskCheckCommand command,
            PreTradeRiskLimitContext limitContext
    ) {
        List<PreTradeRiskRuleCheckResult> ruleResults = new ArrayList<>();
        ruleResults.add(checkPositiveQuantity(command));
        ruleResults.add(checkLimitPriceRequired(command));
        ruleResults.add(checkPositiveLimitPrice(command));
        ruleResults.add(checkMaxOrderQuantity(command, limitContext));
        ruleResults.add(checkMaxOrderNotional(command, limitContext));

        List<PreTradeRiskRuleCheckResult> failedResults = ruleResults.stream()
                .filter(result -> result.status() == PreTradeRiskRuleStatus.FAILED)
                .toList();
        if (!failedResults.isEmpty()) {
            return reject(command, failedResults.getFirst().message(), ruleResults);
        }
        return approve(command, ruleResults);
    }

    private PreTradeRiskRuleCheckResult checkPositiveQuantity(PreTradeRiskCheckCommand command) {
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

    private PreTradeRiskRuleCheckResult checkLimitPriceRequired(PreTradeRiskCheckCommand command) {
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

    private PreTradeRiskRuleCheckResult checkPositiveLimitPrice(PreTradeRiskCheckCommand command) {
        if (command.limitPrice() == null) {
            return skipped(
                    PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE,
                    "limitPrice is not present",
                    null,
                    "0"
            );
        }
        if (command.limitPrice() != null && command.limitPrice().compareTo(BigDecimal.ZERO) <= 0) {
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

    private PreTradeRiskRuleCheckResult checkMaxOrderQuantity(
            PreTradeRiskCheckCommand command,
            PreTradeRiskLimitContext limitContext
    ) {
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

    private PreTradeRiskRuleCheckResult checkMaxOrderNotional(
            PreTradeRiskCheckCommand command,
            PreTradeRiskLimitContext limitContext
    ) {
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

    private PreTradeRiskRuleCheckResult passed(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.PASSED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    private PreTradeRiskRuleCheckResult failed(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.FAILED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    private PreTradeRiskRuleCheckResult skipped(
            PreTradeRiskRuleCode ruleCode,
            String message,
            String evaluatedValue,
            String thresholdValue
    ) {
        return new PreTradeRiskRuleCheckResult(
                ruleCode,
                PreTradeRiskRuleStatus.SKIPPED,
                message,
                evaluatedValue,
                thresholdValue
        );
    }

    private String valueOf(Object value) {
        return value == null ? null : value.toString();
    }

    private String orderNotionalValue(PreTradeRiskCheckCommand command) {
        if (command.requestedQty() == null || command.limitPrice() == null) {
            return null;
        }
        return valueOf(command.requestedQty().multiply(command.limitPrice()));
    }
}
