package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.OrderExecutionEvent;

import java.util.Optional;
import java.util.UUID;

public interface OrderExecutionEventRepository {

    OrderExecutionEvent save(OrderExecutionEvent event);

    Optional<OrderExecutionEvent> findByEventId(UUID eventId);
}
