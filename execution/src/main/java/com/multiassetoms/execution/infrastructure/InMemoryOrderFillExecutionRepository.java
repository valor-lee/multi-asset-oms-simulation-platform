package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.OrderFillExecutionRepository;
import com.multiassetoms.execution.model.OrderFillExecution;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderFillExecutionRepository implements OrderFillExecutionRepository {

    private final Map<UUID, OrderFillExecution> fillExecutionsById = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, OrderFillExecution>> fillExecutionsByOrderId =
            new ConcurrentHashMap<>();

    @Override
    public OrderFillExecution save(OrderFillExecution fillExecution) {
        fillExecutionsById.put(fillExecution.fillExecutionId(), fillExecution);
        fillExecutionsByOrderId
                .computeIfAbsent(fillExecution.orderId(), ignored -> new ConcurrentHashMap<>())
                .put(fillExecution.fillExecutionId(), fillExecution);
        return fillExecution;
    }

    @Override
    public Optional<OrderFillExecution> findByFillExecutionId(UUID fillExecutionId) {
        return Optional.ofNullable(fillExecutionsById.get(fillExecutionId));
    }

    @Override
    public List<OrderFillExecution> findByOrderId(UUID orderId) {
        Map<UUID, OrderFillExecution> fillExecutions = fillExecutionsByOrderId.get(orderId);
        if (fillExecutions == null) {
            return List.of();
        }
        return List.copyOf(fillExecutions.values());
    }
}
