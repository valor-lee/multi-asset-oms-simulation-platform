package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionRequestException;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecutionSimulationRequest(
        UUID simulationId,
        BigDecimal fillQuantity,
        BigDecimal slippageRate
) {

    public UUID requireSimulationId() {
        if (simulationId == null) {
            throw new ExecutionRequestException("simulationId is required");
        }
        return simulationId;
    }

    public BigDecimal requireFillQuantity() {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExecutionRequestException("fillQuantity must be greater than zero");
        }
        return fillQuantity;
    }

    public BigDecimal requireSlippageRate() {
        if (slippageRate == null
                || slippageRate.compareTo(BigDecimal.ZERO) < 0
                || slippageRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new ExecutionRequestException(
                    "slippageRate must be zero or greater and less than one"
            );
        }
        return slippageRate;
    }
}
