package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.execution.application.DuplicateOpenOrderQueryService;
import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.infrastructure.InMemoryMarketPriceRepository;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import com.multiassetoms.intentgeneration.infrastructure.InMemoryOrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskLimitContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleCode;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRuleStatus;
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
    private final InMemoryOrderIntentRepository orderIntentRepository = new InMemoryOrderIntentRepository();
    private final InMemoryMarketPriceRepository marketPriceRepository = new InMemoryMarketPriceRepository();
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final MarketPriceService marketPriceService = new MarketPriceService(
            marketPriceRepository,
            fixedClock
    );
    private final DuplicateOpenOrderQueryService duplicateOpenOrderQueryService =
            new DuplicateOpenOrderQueryService(orderRepository);
    private final PreTradeRiskOrderIntentService service = new PreTradeRiskOrderIntentService(
            riskCheckService,
            orderIntentRepository,
            marketPriceService,
            duplicateOpenOrderQueryService
    );

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
        assertEquals(
                OrderIntentStatus.RISK_APPROVED,
                orderIntentRepository.findByIntentId(intent.intentId()).orElseThrow().status()
        );
    }

    @Test
    void evaluatesOrderIntentByIntentId() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000109"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );
        orderIntentRepository.save(intent);

        PreTradeRiskOrderIntentResult result = service.evaluate(
                intent.intentId(),
                PreTradeRiskCheckContext.empty()
        );

        assertEquals(OrderIntentStatus.RISK_APPROVED, result.intent().status());
        assertEquals(PreTradeRiskDecision.APPROVED, result.riskCheckResult().decision());
    }

    @Test
    void rejectsWhenOrderIntentDoesNotExist() {
        UUID missingIntentId = UUID.fromString("00000000-0000-0000-0000-000000000110");

        OrderIntentNotFoundException exception = assertThrows(
                OrderIntentNotFoundException.class,
                () -> service.evaluate(missingIntentId, PreTradeRiskCheckContext.empty())
        );

        assertEquals("order intent not found", exception.getMessage());
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
        assertEquals(
                OrderIntentStatus.RISK_REJECTED,
                orderIntentRepository.findByIntentId(intent.intentId()).orElseThrow().status()
        );
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

    @Test
    void evaluatesWithLatestPriceBand() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000104"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );
        marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-05-09T08:59:00Z")
        );

        PreTradeRiskOrderIntentResult result = service.evaluateWithLatestPriceBand(
                intent,
                PreTradeRiskCheckContext.empty(),
                new BigDecimal("0.10")
        );

        assertEquals(OrderIntentStatus.RISK_APPROVED, result.intent().status());
        assertEquals(PreTradeRiskDecision.APPROVED, result.riskCheckResult().decision());
        assertEquals(
                PreTradeRiskRuleStatus.PASSED,
                result.riskCheckResult().ruleResults().stream()
                        .filter(ruleResult -> ruleResult.ruleCode() == PreTradeRiskRuleCode.PRICE_BAND)
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
        assertEquals(
                "49500.00..60500.00",
                result.riskCheckResult().ruleResults().stream()
                        .filter(ruleResult -> ruleResult.ruleCode() == PreTradeRiskRuleCode.PRICE_BAND)
                        .findFirst()
                        .orElseThrow()
                        .thresholdValue()
        );
    }

    @Test
    void rejectsWhenLimitPriceIsOutsideLatestPriceBand() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000105"),
                new BigDecimal("10"),
                new BigDecimal("70000"),
                OrderIntentStatus.CREATED
        );
        marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-05-09T08:59:00Z")
        );

        PreTradeRiskOrderIntentResult result = service.evaluateWithLatestPriceBand(
                intent,
                PreTradeRiskCheckContext.empty(),
                new BigDecimal("0.10")
        );

        assertEquals(OrderIntentStatus.RISK_REJECTED, result.intent().status());
        assertEquals(PreTradeRiskDecision.REJECTED, result.riskCheckResult().decision());
        assertEquals("limitPrice is outside price band", result.riskCheckResult().reason());
    }

    @Test
    void rejectsWhenLatestMarketPriceDoesNotExist() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000106"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );

        MarketPriceNotFoundException exception = assertThrows(
                MarketPriceNotFoundException.class,
                () -> service.evaluateWithLatestPriceBand(
                        intent,
                        PreTradeRiskCheckContext.empty(),
                        new BigDecimal("0.10")
                )
        );

        assertEquals("market price not found", exception.getMessage());
    }

    @Test
    void rejectsWhenDuplicateOpenOrderExistsDuringLatestPriceBandEvaluation() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000107"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );
        UUID duplicateOrderId = UUID.fromString("00000000-0000-0000-0000-000000000207");
        orderRepository.save(openOrder(
                duplicateOrderId,
                UUID.fromString("00000000-0000-0000-0000-000000000307")
        ));
        marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-05-09T08:59:00Z")
        );

        PreTradeRiskOrderIntentResult result = service.evaluateWithLatestPriceBandAndDuplicateOpenOrder(
                intent,
                PreTradeRiskCheckContext.empty(),
                new BigDecimal("0.10")
        );

        assertEquals(OrderIntentStatus.RISK_REJECTED, result.intent().status());
        assertEquals(PreTradeRiskDecision.REJECTED, result.riskCheckResult().decision());
        assertEquals("duplicate open order exists", result.riskCheckResult().reason());
        assertEquals(
                duplicateOrderId.toString(),
                result.riskCheckResult().ruleResults().stream()
                        .filter(ruleResult -> ruleResult.ruleCode() == PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER)
                        .findFirst()
                        .orElseThrow()
                        .evaluatedValue()
        );
    }

    @Test
    void approvesWhenNoDuplicateOpenOrderExistsDuringLatestPriceBandEvaluation() {
        OrderIntent intent = createdIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000108"),
                new BigDecimal("10"),
                new BigDecimal("55000"),
                OrderIntentStatus.CREATED
        );
        marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-05-09T08:59:00Z")
        );

        PreTradeRiskOrderIntentResult result = service.evaluateWithLatestPriceBandAndDuplicateOpenOrder(
                intent,
                PreTradeRiskCheckContext.empty(),
                new BigDecimal("0.10")
        );

        assertEquals(OrderIntentStatus.RISK_APPROVED, result.intent().status());
        assertEquals(PreTradeRiskDecision.APPROVED, result.riskCheckResult().decision());
        assertEquals(
                PreTradeRiskRuleStatus.PASSED,
                result.riskCheckResult().ruleResults().stream()
                        .filter(ruleResult -> ruleResult.ruleCode() == PreTradeRiskRuleCode.DUPLICATE_OPEN_ORDER)
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
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

    private Order openOrder(UUID orderId, UUID intentId) {
        Instant createdAt = Instant.parse("2026-05-08T01:00:00Z");
        return new Order(
                orderId,
                intentId,
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                OrderStatus.ACKED,
                createdAt,
                createdAt
        );
    }
}
