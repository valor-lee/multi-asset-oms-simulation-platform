package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOrderIntentResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreTradeRiskOrderIntentServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-09T00:00:00Z"), ZoneOffset.UTC);
    private final PreTradeRiskCheckService riskCheckService = new PreTradeRiskCheckService(fixedClock);
    private final PreTradeRiskOrderIntentService service = new PreTradeRiskOrderIntentService(riskCheckService);

    @Test
    void transitionsCreatedIntentToRiskApprovedWhenRiskCheckApproves() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );

        PreTradeRiskOrderIntentResult result = service.evaluate(intent);

        assertEquals(OrderIntentStatus.RISK_APPROVED, result.intent().status());
        assertEquals(PreTradeRiskDecision.APPROVED, result.riskCheckResult().decision());
        assertEquals(intent.createdAt(), result.intent().createdAt());
        assertEquals(Instant.parse("2026-05-09T00:00:00Z"), result.intent().updatedAt());
        assertEquals(intent.intentId(), result.riskCheckResult().intentId());
    }

    @Test
    void transitionsCreatedIntentToRiskRejectedWhenRiskCheckRejects() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                new BigDecimal("11"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );

        PreTradeRiskOrderIntentResult result = service.evaluate(
                intent,
                new PreTradeRiskCheckContext(
                        new PreTradeRiskLimitContext(new BigDecimal("10")),
                        null
                )
        );

        assertEquals(OrderIntentStatus.RISK_REJECTED, result.intent().status());
        assertEquals(PreTradeRiskDecision.REJECTED, result.riskCheckResult().decision());
        assertEquals("requestedQty exceeds maxOrderQty", result.riskCheckResult().reason());
    }

    @Test
    void rejectsRiskEvaluationForNonCreatedIntent() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000103"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.RISK_APPROVED
        );

        PreTradeRiskTransitionException exception = assertThrows(
                PreTradeRiskTransitionException.class,
                () -> service.evaluate(intent)
        );

        assertEquals("only CREATED order intents can be evaluated by pre-trade risk", exception.getMessage());
    }

    private OrderIntent createdIntent(
            UUID intentId,
            BigDecimal requestedQty,
            BigDecimal limitPrice,
            OrderIntentStatus status
    ) {
        Instant createdAt = Instant.parse("2026-05-08T00:00:00Z");
        return new OrderIntent(
                intentId,
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                "manual-1",
                OrderSide.BUY,
                OrderType.LIMIT,
                requestedQty,
                limitPrice,
                TimeInForce.DAY,
                "manual order",
                status,
                "intent-key",
                "operator",
                createdAt,
                createdAt
        );
    }
}
