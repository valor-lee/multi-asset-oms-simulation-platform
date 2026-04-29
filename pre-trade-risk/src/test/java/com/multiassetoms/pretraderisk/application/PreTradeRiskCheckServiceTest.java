package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

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
    }
}
