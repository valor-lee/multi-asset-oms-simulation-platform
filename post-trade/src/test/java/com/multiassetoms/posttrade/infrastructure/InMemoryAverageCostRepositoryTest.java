package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostSnapshot;
import com.multiassetoms.posttrade.model.PositionKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAverageCostRepositoryTest {

    private final InMemoryAverageCostRepository repository = new InMemoryAverageCostRepository();

    @Test
    void savesAndFindsEntryById() {
        AverageCostEntry entry = entry(UUID.fromString("00000000-0000-0000-0000-000000074001"));

        repository.save(entry);

        Optional<AverageCostEntry> found = repository.findByEntryId(entry.entryId());

        assertTrue(found.isPresent());
        assertEquals(entry, found.orElseThrow());
    }

    @Test
    void savesAndFindsEntryByTradeId() {
        AverageCostEntry entry = entry(UUID.fromString("00000000-0000-0000-0000-000000074002"));

        repository.save(entry);

        Optional<AverageCostEntry> found = repository.findByTradeId(entry.tradeId());

        assertTrue(found.isPresent());
        assertEquals(entry, found.orElseThrow());
    }

    @Test
    void updatesCurrentAverageCostSnapshot() {
        AverageCostEntry entry = entry(UUID.fromString("00000000-0000-0000-0000-000000074003"));

        repository.save(entry);

        AverageCostSnapshot snapshot = repository.currentAverageCost(new PositionKey("portfolio-1", "005930"));

        assertEquals(new BigDecimal("10"), snapshot.quantity());
        assertEquals(new BigDecimal("550100"), snapshot.costBasis());
        assertEquals(new BigDecimal("55010"), snapshot.averageCost());
    }

    @Test
    void returnsEmptySnapshotWhenAverageCostDoesNotExist() {
        AverageCostSnapshot snapshot = repository.currentAverageCost(new PositionKey("portfolio-2", "000660"));

        assertEquals("portfolio-2", snapshot.portfolioId());
        assertEquals("000660", snapshot.instrumentId());
        assertEquals(BigDecimal.ZERO, snapshot.quantity());
        assertEquals(BigDecimal.ZERO, snapshot.costBasis());
        assertEquals(BigDecimal.ZERO, snapshot.averageCost());
    }

    private AverageCostEntry entry(UUID entryId) {
        return new AverageCostEntry(
                entryId,
                UUID.fromString("00000000-0000-0000-0000-000000073001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550100"),
                new BigDecimal("10"),
                new BigDecimal("550100"),
                new BigDecimal("55010"),
                Instant.parse("2026-06-21T00:00:00Z")
        );
    }
}
