package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskControlContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskExposureContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskMarketContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOpenOrderContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreTradeRiskCheckServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T00:00:00Z"), ZoneOffset.UTC);
    private final PreTradeRiskCheckService service = new PreTradeRiskCheckService(fixedClock);

    @Test
    void approvesValidLimitOrderIntent() {
        PreTradeRiskCheckResult result = service.evaluateBasicRules(new PreTradeRiskCheckCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000")
        ));

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals("approved", result.reason());
        assertEquals(Instant.parse("2026-04-29T00:00:00Z"), result.checkedAt());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.POSITIVE_QUANTITY).status());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.LIMIT_PRICE_REQUIRED).status());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_QUANTITY).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_POSITION_QUANTITY).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.PRICE_BAND).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.KILL_SWITCH).status());
    }

    @Test
    void rejectsOrderIntentWithNonPositiveQuantity() {
        PreTradeRiskCheckResult result = service.evaluateBasicRules(new PreTradeRiskCheckCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                BigDecimal.ZERO,
                new BigDecimal("55000")
        ));

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("requestedQty must be greater than zero", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.POSITIVE_QUANTITY).status());
    }

    @Test
    void rejectsLimitOrderIntentWithoutLimitPrice() {
        PreTradeRiskCheckResult result = service.evaluateBasicRules(new PreTradeRiskCheckCommand(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                null
        ));

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("limitPrice is required for LIMIT orders", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.LIMIT_PRICE_REQUIRED).status());
        assertEquals(PreTradeRiskRuleStatus.SKIPPED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.POSITIVE_LIMIT_PRICE).status());
    }

    @Test
    void passesWhenRequestedQuantityIsWithinMaxOrderQuantity() {
        PreTradeRiskCheckResult result = service.evaluateWithLimits(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskLimitContext(new BigDecimal("10"))
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_QUANTITY).status());
    }

    @Test
    void rejectsWhenRequestedQuantityExceedsMaxOrderQuantity() {
        PreTradeRiskCheckResult result = service.evaluateWithLimits(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000005"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("11"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskLimitContext(new BigDecimal("10"))
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("requestedQty exceeds maxOrderQty", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_QUANTITY).status());
    }

    @Test
    void passesWhenOrderNotionalIsWithinMaxOrderNotional() {
        PreTradeRiskCheckResult result = service.evaluateWithLimits(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000006"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskLimitContext(null, new BigDecimal("550000"))
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL).status());
    }

    @Test
    void rejectsWhenOrderNotionalExceedsMaxOrderNotional() {
        PreTradeRiskCheckResult result = service.evaluateWithLimits(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000007"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskLimitContext(null, new BigDecimal("549999"))
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("order notional exceeds maxOrderNotional", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_ORDER_NOTIONAL).status());
    }

    @Test
    void passesWhenExpectedPositionIsWithinMaxPositionQuantity() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000008"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        new PreTradeRiskLimitContext(
                                null,
                                null,
                                new BigDecimal("100")
                        ),
                        new PreTradeRiskExposureContext(new BigDecimal("90"))
                )
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_POSITION_QUANTITY).status());
    }

    @Test
    void rejectsWhenExpectedPositionExceedsMaxPositionQuantity() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000009"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        new PreTradeRiskLimitContext(
                                null,
                                null,
                                new BigDecimal("100")
                        ),
                        new PreTradeRiskExposureContext(new BigDecimal("91"))
                )
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("expected position exceeds maxPositionQty", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.MAX_POSITION_QUANTITY).status());
    }

    @Test
    void passesWhenDuplicateOpenOrderDoesNotExist() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000010"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        new PreTradeRiskOpenOrderContext(false)
                )
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER).status());
    }

    @Test
    void rejectsWhenDuplicateOpenOrderExists() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000011"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        new PreTradeRiskOpenOrderContext(true)
                )
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("duplicate open order exists", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER).status());
    }

    @Test
    void passesWhenLimitPriceIsWithinPriceBand() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000012"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        PreTradeRiskOpenOrderContext.empty(),
                        new PreTradeRiskMarketContext(new BigDecimal("50000"), new BigDecimal("60000")),
                        PreTradeRiskControlContext.empty()
                )
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.PRICE_BAND).status());
    }

    @Test
    void rejectsWhenLimitPriceIsOutsidePriceBand() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000013"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("61000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        PreTradeRiskOpenOrderContext.empty(),
                        new PreTradeRiskMarketContext(new BigDecimal("50000"), new BigDecimal("60000")),
                        PreTradeRiskControlContext.empty()
                )
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("limitPrice is outside price band", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.PRICE_BAND).status());
    }

    @Test
    void passesWhenKillSwitchIsDisabled() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000014"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        PreTradeRiskOpenOrderContext.empty(),
                        PreTradeRiskMarketContext.empty(),
                        new PreTradeRiskControlContext(false)
                )
        );

        assertEquals(PreTradeRiskDecision.APPROVED, result.decision());
        assertEquals(PreTradeRiskRuleStatus.PASSED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.KILL_SWITCH).status());
    }

    @Test
    void rejectsWhenKillSwitchIsEnabled() {
        PreTradeRiskCheckResult result = service.evaluateWithContext(
                new PreTradeRiskCheckCommand(
                        UUID.fromString("00000000-0000-0000-0000-000000000015"),
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        new BigDecimal("55000")
                ),
                new PreTradeRiskCheckContext(
                        PreTradeRiskLimitContext.empty(),
                        PreTradeRiskExposureContext.empty(),
                        PreTradeRiskOpenOrderContext.empty(),
                        PreTradeRiskMarketContext.empty(),
                        new PreTradeRiskControlContext(true)
                )
        );

        assertEquals(PreTradeRiskDecision.REJECTED, result.decision());
        assertEquals("kill switch is enabled", result.reason());
        assertEquals(PreTradeRiskRuleStatus.FAILED,
                ruleResultsByCode(result).get(PreTradeRiskRuleCode.KILL_SWITCH).status());
    }

    private Map<PreTradeRiskRuleCode, PreTradeRiskRuleCheckResult> ruleResultsByCode(
            PreTradeRiskCheckResult result
    ) {
        return result.ruleResults().stream()
                .collect(Collectors.toMap(
                        PreTradeRiskRuleCheckResult::ruleCode,
                        ruleResult -> ruleResult
                ));
    }
}
