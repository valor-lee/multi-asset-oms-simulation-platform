package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
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
        PreTradeRiskCheckResult result = service.check(new PreTradeRiskCheckCommand(
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
    }

    @Test
    void rejectsOrderIntentWithNonPositiveQuantity() {
        PreTradeRiskCheckResult result = service.check(new PreTradeRiskCheckCommand(
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
        PreTradeRiskCheckResult result = service.check(new PreTradeRiskCheckCommand(
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
