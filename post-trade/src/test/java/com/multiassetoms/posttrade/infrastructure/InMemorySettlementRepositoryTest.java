package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.model.Settlement;
import com.multiassetoms.posttrade.model.SettlementStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySettlementRepositoryTest {

    private final InMemorySettlementRepository repository = new InMemorySettlementRepository();

    @Test
    void savesAndFindsSettlementBySettlementId() {
        Settlement settlement = createSettlement(
                UUID.fromString("00000000-0000-0000-0000-000000008001"),
                UUID.fromString("00000000-0000-0000-0000-000000007001")
        );

        repository.save(settlement);

        Optional<Settlement> found = repository.findBySettlementId(settlement.settlementId());
        assertTrue(found.isPresent());
        assertEquals(settlement, found.get());
    }

    @Test
    void savesAndFindsSettlementByTradeId() {
        Settlement settlement = createSettlement(
                UUID.fromString("00000000-0000-0000-0000-000000008002"),
                UUID.fromString("00000000-0000-0000-0000-000000007002")
        );

        repository.save(settlement);

        Optional<Settlement> found = repository.findByTradeId(settlement.tradeId());
        assertTrue(found.isPresent());
        assertEquals(settlement, found.get());
    }

    @Test
    void returnsEmptyWhenSettlementDoesNotExist() {
        Optional<Settlement> found = repository.findByTradeId(
                UUID.fromString("00000000-0000-0000-0000-000000007099")
        );

        assertTrue(found.isEmpty());
    }

    private Settlement createSettlement(UUID settlementId, UUID tradeId) {
        Instant createdAt = Instant.parse("2026-05-22T01:00:00Z");
        return new Settlement(
                settlementId,
                tradeId,
                LocalDate.parse("2026-05-24"),
                SettlementStatus.PENDING,
                createdAt,
                null,
                createdAt
        );
    }
}
