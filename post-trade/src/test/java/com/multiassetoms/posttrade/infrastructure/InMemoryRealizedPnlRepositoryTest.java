package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRealizedPnlRepositoryTest {

    private final InMemoryRealizedPnlRepository repository =
            new InMemoryRealizedPnlRepository();

    @Test
    void savesAndFindsEntryByEntryId() {
        RealizedPnlEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000013001"),
                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                new BigDecimal("10000")
        );

        repository.save(entry);

        Optional<RealizedPnlEntry> found = repository.findByEntryId(entry.entryId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void savesAndFindsEntryByTradeId() {
        RealizedPnlEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000013002"),
                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                new BigDecimal("-5000")
        );

        repository.save(entry);

        Optional<RealizedPnlEntry> found = repository.findByTradeId(entry.tradeId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void accumulatesCurrentRealizedPnlByPortfolio() {
        RealizedPnlEntry firstEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000013003"),
                UUID.fromString("00000000-0000-0000-0000-000000009003"),
                new BigDecimal("10000")
        );
        RealizedPnlEntry secondEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000013004"),
                UUID.fromString("00000000-0000-0000-0000-000000009004"),
                new BigDecimal("-3000")
        );

        repository.save(firstEntry);
        repository.save(secondEntry);

        assertEquals(new BigDecimal("7000"), repository.currentRealizedPnl("portfolio-1"));
    }

    @Test
    void returnsZeroWhenRealizedPnlDoesNotExist() {
        assertEquals(BigDecimal.ZERO, repository.currentRealizedPnl("portfolio-2"));
    }

    private RealizedPnlEntry createEntry(
            UUID entryId,
            UUID tradeId,
            BigDecimal realizedPnl
    ) {
        return new RealizedPnlEntry(
                entryId,
                tradeId,
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("54000"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                realizedPnl,
                Instant.parse("2026-05-27T01:00:00Z")
        );
    }
}
