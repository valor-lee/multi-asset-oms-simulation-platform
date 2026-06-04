package com.multiassetoms.intentgeneration.application;

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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIntentQueryServiceTest {

    private final InMemoryOrderIntentRepository repository = new InMemoryOrderIntentRepository();
    private final OrderIntentQueryService service = new OrderIntentQueryService(repository);

    @Test
    void getsOrderIntentByIntentId() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000026001"),
                "query-key-1"
        );
        repository.save(intent);

        OrderIntent found = service.getByIntentId(intent.intentId());

        assertEquals(intent, found);
    }

    @Test
    void getsOrderIntentByIdempotencyKey() {
        OrderIntent intent = orderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000026002"),
                "query-key-2"
        );
        repository.save(intent);

        OrderIntent found = service.getByIdempotencyKey("query-key-2");

        assertEquals(intent, found);
    }

    @Test
    void throwsWhenOrderIntentDoesNotExist() {
        OrderIntentNotFoundException exception = assertThrows(
                OrderIntentNotFoundException.class,
                () -> service.getByIntentId(UUID.fromString("00000000-0000-0000-0000-000000026003"))
        );

        assertEquals("order intent not found", exception.getMessage());
    }

    private OrderIntent orderIntent(UUID intentId, String idempotencyKey) {
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
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
