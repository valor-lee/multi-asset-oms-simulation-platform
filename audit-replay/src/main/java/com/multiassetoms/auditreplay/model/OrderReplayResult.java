package com.multiassetoms.auditreplay.model;

import com.multiassetoms.execution.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderReplayResult(
        UUID orderId,
        OrderStatus initialStatus,
        OrderStatus replayedStatus,
        BigDecimal orderQuantity,
        BigDecimal replayedFilledQuantity,
        int appliedEventCount,
        Instant replayedAt
) {
}
