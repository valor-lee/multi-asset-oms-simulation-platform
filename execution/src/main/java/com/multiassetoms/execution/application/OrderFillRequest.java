package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionRequestException;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderFillRequest(
        UUID fillExecutionId,
        BigDecimal fillQuantity,
        BigDecimal fillPrice,
        BigDecimal feeAmount,
        BigDecimal taxAmount
) {

    public UUID requireFillExecutionId() {
        if (fillExecutionId == null) {
            throw new ExecutionRequestException("fillExecutionId is required");
        }
        return fillExecutionId;
    }

    public BigDecimal requireFillQuantity() {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExecutionRequestException("fillQuantity must be greater than zero");
        }
        return fillQuantity;
    }

    public BigDecimal validatedFillPrice() {
        if (fillPrice != null && fillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExecutionRequestException("fillPrice must be greater than zero");
        }
        return fillPrice;
    }

    public BigDecimal validatedFeeAmount() {
        if (feeAmount != null && feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ExecutionRequestException("feeAmount must be zero or greater");
        }
        return feeAmount;
    }

    public BigDecimal validatedTaxAmount() {
        if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ExecutionRequestException("taxAmount must be zero or greater");
        }
        return taxAmount;
    }
}
