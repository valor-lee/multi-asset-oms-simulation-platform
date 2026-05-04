package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderIntentValidationException;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIntentFactoryTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-25T00:00:00Z"), ZoneOffset.UTC);
    private final OrderIntentFactory factory = new OrderIntentFactory(validator, fixedClock);

    @Test
    void createsLimitOrderIntentFromValidCommand() {
        OrderIntent intent = factory.create(new CreateOrderIntentCommand(
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                "manual-1",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10.00000000"),
                new BigDecimal("55000.0000"),
                TimeInForce.DAY,
                "rebalance drift",
                "manual-1-key",
                "operator"
        ));

        assertNotNull(intent.intentId());
        assertEquals(OrderIntentStatus.CREATED, intent.status());
        assertEquals(0, new BigDecimal("10").compareTo(intent.requestedQty()));
        assertEquals(0, new BigDecimal("55000").compareTo(intent.limitPrice()));
        assertEquals("manual-1-key", intent.idempotencyKey());
        assertEquals(Instant.parse("2026-04-25T00:00:00Z"), intent.createdAt());
        assertEquals(intent.createdAt(), intent.updatedAt());
    }

    @Test
    void generatesIdempotencyKeyWhenMissing() {
        OrderIntent intent = factory.create(new CreateOrderIntentCommand(
                "portfolio-1",
                "005930",
                OrderIntentSourceType.STRATEGY,
                "signal-1",
                OrderSide.SELL,
                OrderType.MARKET,
                new BigDecimal("3"),
                null,
                null,
                "signal exit",
                " ",
                "strategy-engine"
        ));

        assertNotNull(intent.idempotencyKey());
        assertNull(intent.limitPrice());
    }

    @Test
    void rejectsLimitOrderWithoutLimitPrice() {
        OrderIntentValidationException exception = assertThrows(OrderIntentValidationException.class,
                () -> factory.create(new CreateOrderIntentCommand(
                        "portfolio-1",
                        "005930",
                        OrderIntentSourceType.REBALANCING,
                        "rebalance-1",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("1"),
                        null,
                        TimeInForce.DAY,
                        null,
                        null,
                        "pm"
                )));

        assertEquals("limitPrice is required for LIMIT orders", exception.getMessage());
    }

    @Test
    void rejectsMarketOrderWithLimitPrice() {
        OrderIntentValidationException exception = assertThrows(OrderIntentValidationException.class,
                () -> factory.create(new CreateOrderIntentCommand(
                        "portfolio-1",
                        "005930",
                        OrderIntentSourceType.REBALANCING,
                        "rebalance-1",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        new BigDecimal("1"),
                        new BigDecimal("100"),
                        TimeInForce.DAY,
                        null,
                        null,
                        "pm"
                )));

        assertEquals("limitPrice must be null for MARKET orders", exception.getMessage());
    }
}
