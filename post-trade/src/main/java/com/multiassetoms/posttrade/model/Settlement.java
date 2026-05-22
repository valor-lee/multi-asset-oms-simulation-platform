package com.multiassetoms.posttrade.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Settlement(
        UUID settlementId,
        UUID tradeId,
        LocalDate settlementDate,
        SettlementStatus status,
        Instant createdAt,
        Instant settledAt,
        Instant updatedAt
) {
}
