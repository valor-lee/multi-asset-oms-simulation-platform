package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.PositionKey;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPositionLedgerRepositoryTest {

    private final InMemoryPositionLedgerRepository repository =
            new InMemoryPositionLedgerRepository();

    @Test
    void savesAndFindsEntryByEntryId() {
        PositionLedgerEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000010001"),
                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                new BigDecimal("10")
        );

        repository.save(entry);

        Optional<PositionLedgerEntry> found = repository.findByEntryId(entry.entryId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void savesAndFindsEntryByTradeId() {
        PositionLedgerEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000010002"),
                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                new BigDecimal("10")
        );

        repository.save(entry);

        Optional<PositionLedgerEntry> found = repository.findByTradeId(entry.tradeId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void accumulatesCurrentPosition() {
        PositionLedgerEntry firstEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000010003"),
                UUID.fromString("00000000-0000-0000-0000-000000009003"),
                new BigDecimal("10")
        );
        PositionLedgerEntry secondEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000010004"),
                UUID.fromString("00000000-0000-0000-0000-000000009004"),
                new BigDecimal("-4")
        );

        repository.save(firstEntry);
        repository.save(secondEntry);

        assertEquals(
                new BigDecimal("6"),
                repository.currentPosition(new PositionKey("portfolio-1", "005930"))
        );
    }

    @Test
    void returnsZeroWhenPositionDoesNotExist() {
        assertEquals(
                BigDecimal.ZERO,
                repository.currentPosition(new PositionKey("portfolio-1", "000660"))
        );
    }

    private PositionLedgerEntry createEntry(
            UUID entryId,
            UUID tradeId,
            BigDecimal quantityDelta
    ) {
        return new PositionLedgerEntry(
                entryId,
                tradeId,
                "portfolio-1",
                "005930",
                quantityDelta.compareTo(BigDecimal.ZERO) >= 0 ? OrderSide.BUY : OrderSide.SELL,
                quantityDelta,
                Instant.parse("2026-05-22T02:00:00Z")
        );
    }
}
