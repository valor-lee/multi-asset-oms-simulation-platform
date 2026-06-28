package com.multiassetoms.execution.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecutionSimulationResult(
        UUID simulationId,
        UUID orderId,
        ExecutionSimulationStatus simulationStatus,
        BigDecimal referencePrice,
        BigDecimal fillPrice,
        BigDecimal requestedFillQuantity,
        BigDecimal slippageRate,
        BigDecimal rejectRate,
        long delayMillis,
        Order order
) {
}
