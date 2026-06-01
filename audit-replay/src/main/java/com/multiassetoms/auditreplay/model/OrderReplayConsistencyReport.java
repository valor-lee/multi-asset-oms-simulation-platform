package com.multiassetoms.auditreplay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderReplayConsistencyReport(
        int totalCount,
        int consistentCount,
        int inconsistentCount,
        BigDecimal inconsistentRatio,
        List<OrderReplayConsistencyResult> results,
        Instant checkedAt
) {
    public OrderReplayConsistencyReport {
        results = List.copyOf(results);
    }
}
