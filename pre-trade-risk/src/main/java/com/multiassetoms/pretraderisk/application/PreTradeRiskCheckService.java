package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOpenOrderContext;
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
        List<PreTradeRiskRuleCheckResult> ruleResults = new ArrayList<>();
        ruleResults.add(evaluatePositiveQuantityRule(command));
        ruleResults.add(evaluateLimitPriceRequiredRule(command));
        ruleResults.add(evaluatePositiveLimitPriceRule(command));
        ruleResults.add(evaluateMaxOrderQuantityRule(command, checkContext.limitContext()));
        ruleResults.add(evaluateMaxOrderNotionalRule(command, checkContext.limitContext()));
        ruleResults.add(evaluateMaxPositionQuantityRule(command, checkContext));
        ruleResults.add(evaluateDuplicateOpenOrderRule(checkContext.openOrderContext()));

        List<PreTradeRiskRuleCheckResult> failedResults = ruleResults.stream()
                .filter(result -> result.status() == PreTradeRiskRuleStatus.FAILED)
                .toList();
        if (!failedResults.isEmpty()) {
            return reject(command, failedResults.getFirst().message(), ruleResults);
        }
        return approve(command, ruleResults);
    }

    private PreTradeRiskRuleCheckResult evaluatePositiveQuantityRule(PreTradeRiskCheckCommand command) {
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

    private PreTradeRiskRuleCheckResult evaluateLimitPriceRequiredRule(PreTradeRiskCheckCommand command) {
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

    private PreTradeRiskRuleCheckResult evaluatePositiveLimitPriceRule(PreTradeRiskCheckCommand command) {
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

    private PreTradeRiskRuleCheckResult evaluateMaxOrderQuantityRule(
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

    private PreTradeRiskRuleCheckResult evaluateMaxOrderNotionalRule(
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

    private PreTradeRiskRuleCheckResult evaluateMaxPositionQuantityRule(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskLimitContext limitContext = checkContext.limitContext();
        PreTradeRiskExposureContext exposureContext = checkContext.exposureContext();
        if (limitContext.maxPositionQty() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                    "maxPositionQty limit is not configured",
                    expectedPositionValue(command, checkContext),
                    null
            );
        }
        if (exposureContext.currentPositionQty() == null || command.requestedQty() == null || command.side() == null) {
            return skipped(
                    PreTradeRiskRuleCode.MAX_POSITION_QUANTITY,
                    "expected position cannot be calculated",
                    expectedPositionValue(command, checkContext),
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

    private PreTradeRiskRuleCheckResult evaluateDuplicateOpenOrderRule(
            PreTradeRiskOpenOrderContext openOrderContext
    ) {
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
                    valueOf(openOrderContext.duplicateOpenOrderExists()),
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

    private String expectedPositionValue(
            PreTradeRiskCheckCommand command,
            PreTradeRiskCheckContext checkContext
    ) {
        PreTradeRiskExposureContext exposureContext = checkContext.exposureContext();
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
