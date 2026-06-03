package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.infrastructure.InMemoryOrderIntentRepository;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentIdempotencyConflictException;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIntentCreatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC);
    private final OrderIntentFactory orderIntentFactory = new OrderIntentFactory(validator, fixedClock);
    private final InMemoryOrderIntentRepository repository = new InMemoryOrderIntentRepository();
    private final OrderIntentCreator orderIntentCreator = new OrderIntentCreator(orderIntentFactory, repository);

    @Test
    void returnsExistingIntentWhenIdempotencyKeyAlreadyExists() {
        CreateOrderIntentCommand command = command("manual-key-1");
        OrderIntent first = orderIntentCreator.create(command);

        OrderIntent second = orderIntentCreator.create(command);

        assertEquals(first, second);
        assertEquals(first.intentId(), second.intentId());
        assertEquals(first.createdAt(), second.createdAt());
    }

    @Test
    void normalizesIdempotencyKeyBeforeLookup() {
        OrderIntent first = orderIntentCreator.create(command("manual-key-2"));

        OrderIntent second = orderIntentCreator.create(command("  manual-key-2  "));

        assertEquals(first, second);
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentPayload() {
        orderIntentCreator.create(command("manual-key-3"));

        OrderIntentIdempotencyConflictException exception = assertThrows(
                OrderIntentIdempotencyConflictException.class,
                () -> orderIntentCreator.create(command("manual-key-3", new BigDecimal("20")))
        );

        assertEquals(
                "idempotencyKey already exists for a different order intent request",
                exception.getMessage()
        );
    }

    private CreateOrderIntentCommand command(String idempotencyKey) {
        return command(idempotencyKey, new BigDecimal("10"));
    }

    private CreateOrderIntentCommand command(String idempotencyKey, BigDecimal requestedQty) {
        return new CreateOrderIntentCommand(
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                null,
                OrderSide.BUY,
                OrderType.LIMIT,
                requestedQty,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "operator order",
                idempotencyKey,
                "operator"
        );
    }
}
