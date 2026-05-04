package com.multiassetoms.intentgeneration.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderIntentCommand(
        @NotBlank String portfolioId,
        @NotBlank String instrumentId,
        @NotNull OrderIntentSourceType sourceType,
        String sourceRefId,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @NotNull @DecimalMin(value = "0.00000001", inclusive = true) BigDecimal requestedQty,
        BigDecimal limitPrice,
        TimeInForce timeInForce,
        String reason,
        String idempotencyKey,
        String createdBy
) {
}
