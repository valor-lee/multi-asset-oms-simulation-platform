package com.multiassetoms.intentgeneration.infrastructure;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrderIntentRepositoryTest {

    private final InMemoryOrderIntentRepository repository = new InMemoryOrderIntentRepository();

    @Test
    void savesAndFindsOrderIntentByIntentId() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "intent-key-1"
        );

        repository.save(intent);

        Optional<OrderIntent> found = repository.findByIntentId(intent.intentId());

        assertTrue(found.isPresent());
        assertEquals(intent, found.get());
    }

    @Test
    void savesAndFindsOrderIntentByIdempotencyKey() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                "intent-key-2"
        );

        repository.save(intent);

        Optional<OrderIntent> found = repository.findByIdempotencyKey("intent-key-2");

        assertTrue(found.isPresent());
        assertEquals(intent.intentId(), found.get().intentId());
    }

    @Test
    void returnsEmptyWhenIdempotencyKeyIsNull() {
        assertTrue(repository.findByIdempotencyKey(null).isEmpty());
    }

    private OrderIntent orderIntent(UUID intentId, String idempotencyKey) {
        Instant now = Instant.parse("2026-05-15T00:00:00Z");
        return new OrderIntent(
                intentId,
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                null,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "manual order",
                OrderIntentStatus.CREATED,
                idempotencyKey,
                "operator",
                now,
                now
        );
    }
}
