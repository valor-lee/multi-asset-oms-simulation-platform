package com.multiassetoms.auditreplay.model;

import com.multiassetoms.execution.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderReplayConsistencyResult(
        UUID orderId,
        boolean consistent,
        List<OrderReplayMismatchReason> mismatchReasons,
        OrderStatus actualStatus,
        OrderStatus replayedStatus,
        BigDecimal actualFilledQuantity,
        BigDecimal replayedFilledQuantity,
        int appliedEventCount,
        Instant checkedAt
) {
    public OrderReplayConsistencyResult {
        mismatchReasons = List.copyOf(mismatchReasons);
    }
}
