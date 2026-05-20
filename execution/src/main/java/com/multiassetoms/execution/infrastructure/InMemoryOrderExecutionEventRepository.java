package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.OrderExecutionEventRepository;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderExecutionEventRepository implements OrderExecutionEventRepository {

    private final Map<UUID, OrderExecutionEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public OrderExecutionEvent save(OrderExecutionEvent event) {
        eventsById.put(event.eventId(), event);
        return event;
    }

    @Override
    public Optional<OrderExecutionEvent> findByEventId(UUID eventId) {
        return Optional.ofNullable(eventsById.get(eventId));
    }
}
