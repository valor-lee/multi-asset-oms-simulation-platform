package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.OrderFillExecutionRepository;
import com.multiassetoms.execution.model.OrderFillExecution;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderFillExecutionRepository implements OrderFillExecutionRepository {

    private final Map<UUID, OrderFillExecution> fillExecutionsById = new ConcurrentHashMap<>();

    @Override
    public OrderFillExecution save(OrderFillExecution fillExecution) {
        fillExecutionsById.put(fillExecution.fillExecutionId(), fillExecution);
        return fillExecution;
    }

    @Override
    public Optional<OrderFillExecution> findByFillExecutionId(UUID fillExecutionId) {
        return Optional.ofNullable(fillExecutionsById.get(fillExecutionId));
    }
}
