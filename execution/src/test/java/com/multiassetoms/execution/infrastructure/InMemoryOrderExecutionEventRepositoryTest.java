package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrderExecutionEventRepositoryTest {

    private final InMemoryOrderExecutionEventRepository repository =
            new InMemoryOrderExecutionEventRepository();

    @Test
    void savesAndFindsExecutionEventById() {
        OrderExecutionEvent event = new OrderExecutionEvent(
                UUID.fromString("00000000-0000-0000-0000-000000004001"),
                UUID.fromString("00000000-0000-0000-0000-000000001001"),
                OrderExecutionEventType.ACKNOWLEDGED,
                Instant.parse("2026-05-21T01:00:00Z")
        );

        repository.save(event);

        Optional<OrderExecutionEvent> found = repository.findByEventId(event.eventId());
        assertTrue(found.isPresent());
        assertEquals(event, found.get());
    }

    @Test
    void returnsEmptyWhenExecutionEventDoesNotExist() {
        Optional<OrderExecutionEvent> found = repository.findByEventId(
                UUID.fromString("00000000-0000-0000-0000-000000004099")
        );

        assertTrue(found.isEmpty());
    }
}
