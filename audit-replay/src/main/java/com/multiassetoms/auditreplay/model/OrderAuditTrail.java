package com.multiassetoms.auditreplay.model;

import java.util.List;
import java.util.UUID;

public record OrderAuditTrail(
        UUID orderId,
        List<OrderAuditEvent> events
) {
}
