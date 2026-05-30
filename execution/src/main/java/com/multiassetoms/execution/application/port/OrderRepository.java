package com.multiassetoms.execution.application.port;

import com.multiassetoms.execution.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByOrderId(UUID orderId);

    Optional<Order> findByIntentId(UUID intentId);

    List<Order> findAll();
}
