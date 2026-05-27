package com.multiassetoms.execution.application.port;

import com.multiassetoms.execution.model.OrderExecutionEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderExecutionEventRepository {

    OrderExecutionEvent save(OrderExecutionEvent event);

    Optional<OrderExecutionEvent> findByEventId(UUID eventId);

    List<OrderExecutionEvent> findByOrderId(UUID orderId);
}
