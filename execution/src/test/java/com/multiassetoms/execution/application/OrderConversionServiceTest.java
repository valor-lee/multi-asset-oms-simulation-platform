package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.infrastructure.InMemoryOrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderConversionServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryOrderIntentRepository orderIntentRepository = new InMemoryOrderIntentRepository();
    private final OrderConversionService service = new OrderConversionService(
            orderRepository,
            orderIntentRepository,
            fixedClock
    );

    @Test
    void convertsRiskApprovedIntentToCreatedOrderAndMarksIntentConverted() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                OrderIntentStatus.RISK_APPROVED
        );
        orderIntentRepository.save(intent);

        OrderConversionResult result = service.convert(intent.intentId());

        assertNotNull(result.order().orderId());
        assertEquals(intent.intentId(), result.order().intentId());
        assertEquals(intent.portfolioId(), result.order().portfolioId());
        assertEquals(intent.instrumentId(), result.order().instrumentId());
        assertEquals(intent.side(), result.order().side());
        assertEquals(intent.orderType(), result.order().orderType());
        assertEquals(intent.requestedQty(), result.order().quantity());
        assertEquals(intent.limitPrice(), result.order().limitPrice());
        assertEquals(intent.timeInForce(), result.order().timeInForce());
        assertEquals(OrderStatus.CREATED, result.order().status());
        assertEquals(Instant.parse("2026-05-17T00:00:00Z"), result.order().createdAt());
        assertEquals(result.order().createdAt(), result.intent().updatedAt());

        assertEquals(OrderIntentStatus.CONVERTED_TO_ORDER, result.intent().status());
        assertEquals(
                OrderIntentStatus.CONVERTED_TO_ORDER,
                orderIntentRepository.findByIntentId(intent.intentId()).orElseThrow().status()
        );
        assertEquals(result.order(), orderRepository.findByIntentId(intent.intentId()).orElseThrow());
    }

    @Test
    void returnsExistingOrderWhenSameIntentIsConvertedAgain() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000502"),
                OrderIntentStatus.RISK_APPROVED
        );
        orderIntentRepository.save(intent);

        OrderConversionResult firstResult = service.convert(intent.intentId());
        OrderConversionResult secondResult = service.convert(intent.intentId());

        assertEquals(firstResult.order().orderId(), secondResult.order().orderId());
        assertEquals(OrderIntentStatus.CONVERTED_TO_ORDER, secondResult.intent().status());
        assertEquals(firstResult.intent().updatedAt(), secondResult.intent().updatedAt());
    }

    @Test
    void returnsExistingOrderWhenConvertedIntentIdIsRequestedAgain() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000503"),
                OrderIntentStatus.RISK_APPROVED
        );
        orderIntentRepository.save(intent);

        OrderConversionResult firstResult = service.convert(intent.intentId());
        OrderConversionResult secondResult = service.convert(intent.intentId());

        assertEquals(firstResult.order().orderId(), secondResult.order().orderId());
        assertEquals(OrderIntentStatus.CONVERTED_TO_ORDER, secondResult.intent().status());
        assertEquals(firstResult.intent().updatedAt(), secondResult.intent().updatedAt());
    }

    @Test
    void doesNotSaveConvertedIntentAgainWhenExistingOrderIsReturned() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000504"),
                OrderIntentStatus.RISK_APPROVED
        );
        orderIntentRepository.save(intent);
        OrderConversionResult firstResult = service.convert(intent.intentId());

        OrderIntent alreadyConvertedIntent = orderIntent(
                intent.intentId(),
                OrderIntentStatus.CONVERTED_TO_ORDER
        );
        orderIntentRepository.save(alreadyConvertedIntent);

        OrderConversionResult secondResult = service.convert(intent.intentId());

        assertEquals(firstResult.order().orderId(), secondResult.order().orderId());
        assertEquals(alreadyConvertedIntent.updatedAt(), secondResult.intent().updatedAt());
        assertEquals(
                alreadyConvertedIntent.updatedAt(),
                orderIntentRepository.findByIntentId(intent.intentId()).orElseThrow().updatedAt()
        );
    }

    @Test
    void rejectsIntentThatIsNotRiskApproved() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000505"),
                OrderIntentStatus.CREATED
        );
        orderIntentRepository.save(intent);

        OrderConversionException exception = assertThrows(
                OrderConversionException.class,
                () -> service.convert(intent.intentId())
        );

        assertEquals("only RISK_APPROVED order intents can be converted to orders", exception.getMessage());
    }

    @Test
    void rejectsMissingIntentId() {
        OrderIntentNotFoundException exception = assertThrows(
                OrderIntentNotFoundException.class,
                () -> service.convert(UUID.fromString("00000000-0000-0000-0000-000000000599"))
        );

        assertEquals("order intent not found", exception.getMessage());
    }

    private OrderIntent orderIntent(UUID intentId, OrderIntentStatus status) {
        Instant createdAt = Instant.parse("2026-05-16T00:00:00Z");
        return new OrderIntent(
                intentId,
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                "manual-1",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "manual order",
                status,
                "intent-key-" + intentId,
                "operator",
                createdAt,
                createdAt
        );
    }
}
