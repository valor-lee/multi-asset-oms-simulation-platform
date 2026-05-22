package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.PositionLedgerRepository;
import com.multiassetoms.posttrade.model.PositionKey;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPositionLedgerRepository implements PositionLedgerRepository {

    private final Map<UUID, PositionLedgerEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entryIdsByTradeId = new ConcurrentHashMap<>();
    private final Map<PositionKey, BigDecimal> currentPositionsByKey = new ConcurrentHashMap<>();

    @Override
    public PositionLedgerEntry save(PositionLedgerEntry entry) {
        entriesById.put(entry.entryId(), entry);
        entryIdsByTradeId.put(entry.tradeId(), entry.entryId());
        currentPositionsByKey.merge(
                new PositionKey(entry.portfolioId(), entry.instrumentId()),
                entry.quantityDelta(),
                BigDecimal::add
        );
        return entry;
    }

    @Override
    public Optional<PositionLedgerEntry> findByEntryId(UUID entryId) {
        return Optional.ofNullable(entriesById.get(entryId));
    }

    @Override
    public Optional<PositionLedgerEntry> findByTradeId(UUID tradeId) {
        UUID entryId = entryIdsByTradeId.get(tradeId);
        if (entryId == null) {
            return Optional.empty();
        }
        return findByEntryId(entryId);
    }

    @Override
    public BigDecimal currentPosition(PositionKey positionKey) {
        return currentPositionsByKey.getOrDefault(positionKey, BigDecimal.ZERO);
    }
}
