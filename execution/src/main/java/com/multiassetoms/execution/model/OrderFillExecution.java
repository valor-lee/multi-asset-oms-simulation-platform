package com.multiassetoms.execution.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderFillExecution(
        UUID fillExecutionId,
        UUID orderId,
        BigDecimal fillQuantity,
        BigDecimal fillPrice,
        BigDecimal feeAmount,
        Instant processedAt
) {
}
