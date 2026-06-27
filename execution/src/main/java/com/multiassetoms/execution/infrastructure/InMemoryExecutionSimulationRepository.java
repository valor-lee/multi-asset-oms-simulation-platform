package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.ExecutionSimulationRepository;
import com.multiassetoms.execution.model.ExecutionSimulationResult;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryExecutionSimulationRepository implements ExecutionSimulationRepository {

    private final Map<UUID, ExecutionSimulationResult> resultsBySimulationId =
            new ConcurrentHashMap<>();

    @Override
    public ExecutionSimulationResult save(ExecutionSimulationResult result) {
        resultsBySimulationId.put(result.simulationId(), result);
        return result;
    }

    @Override
    public Optional<ExecutionSimulationResult> findBySimulationId(UUID simulationId) {
        return Optional.ofNullable(resultsBySimulationId.get(simulationId));
    }
}
