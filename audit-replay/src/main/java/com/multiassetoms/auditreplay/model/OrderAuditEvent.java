package com.multiassetoms.auditreplay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderAuditEvent(
        UUID eventId,
        UUID orderId,
        OrderAuditEventSource source,
        String eventType,
        BigDecimal fillQuantity,
        BigDecimal fillPrice,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        Instant occurredAt
) {
}
