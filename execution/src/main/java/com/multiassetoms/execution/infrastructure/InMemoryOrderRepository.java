package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> ordersById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> orderIdsByIntentId = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        ordersById.put(order.orderId(), order);
        orderIdsByIntentId.put(order.intentId(), order.orderId());
        return order;
    }

    @Override
    public Optional<Order> findByOrderId(UUID orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    @Override
    public Optional<Order> findByIntentId(UUID intentId) {
        UUID orderId = orderIdsByIntentId.get(intentId);
        if (orderId == null) {
            return Optional.empty();
        }
        return findByOrderId(orderId);
    }
}
