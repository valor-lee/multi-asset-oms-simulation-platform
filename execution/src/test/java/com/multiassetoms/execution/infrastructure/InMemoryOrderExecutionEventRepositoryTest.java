package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

    @Test
    void findsExecutionEventsByOrderId() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001002");
        OrderExecutionEvent ackEvent = new OrderExecutionEvent(
                UUID.fromString("00000000-0000-0000-0000-000000004002"),
                orderId,
                OrderExecutionEventType.ACKNOWLEDGED,
                Instant.parse("2026-05-21T01:00:00Z")
        );
        OrderExecutionEvent cancelEvent = new OrderExecutionEvent(
                UUID.fromString("00000000-0000-0000-0000-000000004003"),
                orderId,
                OrderExecutionEventType.CANCEL_CONFIRMED,
                Instant.parse("2026-05-21T01:03:00Z")
        );

        repository.save(ackEvent);
        repository.save(cancelEvent);

        List<OrderExecutionEvent> events = repository.findByOrderId(orderId);

        assertEquals(2, events.size());
        assertTrue(events.contains(ackEvent));
        assertTrue(events.contains(cancelEvent));
    }
}
