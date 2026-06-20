package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.AverageCostRepository;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostSnapshot;
import com.multiassetoms.posttrade.model.PositionKey;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAverageCostRepository implements AverageCostRepository {

    private final Map<UUID, AverageCostEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entryIdsByTradeId = new ConcurrentHashMap<>();
    private final Map<PositionKey, AverageCostSnapshot> snapshotsByKey = new ConcurrentHashMap<>();

    @Override
    public AverageCostEntry save(AverageCostEntry entry) {
        entriesById.put(entry.entryId(), entry);
        entryIdsByTradeId.put(entry.tradeId(), entry.entryId());
        snapshotsByKey.put(
                new PositionKey(entry.portfolioId(), entry.instrumentId()),
                new AverageCostSnapshot(
                        entry.portfolioId(),
                        entry.instrumentId(),
                        entry.positionQuantity(),
                        entry.costBasis(),
                        entry.averageCost(),
                        entry.postedAt()
                )
        );
        return entry;
    }

    @Override
    public Optional<AverageCostEntry> findByEntryId(UUID entryId) {
        return Optional.ofNullable(entriesById.get(entryId));
    }

    @Override
    public Optional<AverageCostEntry> findByTradeId(UUID tradeId) {
        UUID entryId = entryIdsByTradeId.get(tradeId);
        if (entryId == null) {
            return Optional.empty();
        }
        return findByEntryId(entryId);
    }

    @Override
    public AverageCostSnapshot currentAverageCost(PositionKey positionKey) {
        return snapshotsByKey.getOrDefault(
                positionKey,
                AverageCostSnapshot.empty(positionKey.portfolioId(), positionKey.instrumentId())
        );
    }
}
