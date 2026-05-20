package com.multiassetoms.execution.model;

import java.time.Instant;
import java.util.UUID;

public record OrderExecutionEvent(
        UUID eventId,
        UUID orderId,
        OrderExecutionEventType eventType,
        Instant processedAt
) {
}
