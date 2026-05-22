package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCashLedgerRepositoryTest {

    private final InMemoryCashLedgerRepository repository =
            new InMemoryCashLedgerRepository();

    @Test
    void savesAndFindsEntryByEntryId() {
        CashLedgerEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000011001"),
                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                new BigDecimal("-550000")
        );

        repository.save(entry);

        Optional<CashLedgerEntry> found = repository.findByEntryId(entry.entryId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void savesAndFindsEntryByTradeId() {
        CashLedgerEntry entry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000011002"),
                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                new BigDecimal("220000")
        );

        repository.save(entry);

        Optional<CashLedgerEntry> found = repository.findByTradeId(entry.tradeId());
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void accumulatesCurrentCashByPortfolio() {
        CashLedgerEntry firstEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000011003"),
                UUID.fromString("00000000-0000-0000-0000-000000009003"),
                new BigDecimal("-550000")
        );
        CashLedgerEntry secondEntry = createEntry(
                UUID.fromString("00000000-0000-0000-0000-000000011004"),
                UUID.fromString("00000000-0000-0000-0000-000000009004"),
                new BigDecimal("220000")
        );

        repository.save(firstEntry);
        repository.save(secondEntry);

        assertEquals(new BigDecimal("-330000"), repository.currentCash("portfolio-1"));
    }

    @Test
    void returnsZeroWhenCashDoesNotExist() {
        assertEquals(BigDecimal.ZERO, repository.currentCash("portfolio-2"));
    }

    private CashLedgerEntry createEntry(
            UUID entryId,
            UUID tradeId,
            BigDecimal cashDelta
    ) {
        return new CashLedgerEntry(
                entryId,
                tradeId,
                "portfolio-1",
                cashDelta.compareTo(BigDecimal.ZERO) >= 0 ? OrderSide.SELL : OrderSide.BUY,
                cashDelta,
                Instant.parse("2026-05-23T01:00:00Z")
        );
    }
}
