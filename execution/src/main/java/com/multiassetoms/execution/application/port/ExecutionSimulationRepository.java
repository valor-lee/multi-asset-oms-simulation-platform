package com.multiassetoms.execution.application.port;

import com.multiassetoms.execution.model.ExecutionSimulationResult;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionSimulationRepository {

    ExecutionSimulationResult save(ExecutionSimulationResult result);

    Optional<ExecutionSimulationResult> findBySimulationId(UUID simulationId);
}
