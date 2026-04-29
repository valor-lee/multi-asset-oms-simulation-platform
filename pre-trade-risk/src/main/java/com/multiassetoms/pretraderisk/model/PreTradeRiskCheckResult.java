package com.multiassetoms.pretraderisk.model;

import java.time.Instant;
import java.util.UUID;

public record PreTradeRiskCheckResult(
        UUID intentId,
        PreTradeRiskDecision decision,
        String reason,
        Instant checkedAt
) {
}
