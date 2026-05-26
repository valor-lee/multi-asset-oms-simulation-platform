package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.RealizedPnlRepository;
import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRealizedPnlRepository implements RealizedPnlRepository {

    private final Map<UUID, RealizedPnlEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entryIdsByTradeId = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> currentRealizedPnlByPortfolioId = new ConcurrentHashMap<>();

    @Override
    public RealizedPnlEntry save(RealizedPnlEntry entry) {
        entriesById.put(entry.entryId(), entry);
        entryIdsByTradeId.put(entry.tradeId(), entry.entryId());
        currentRealizedPnlByPortfolioId.merge(
                entry.portfolioId(),
                entry.realizedPnl(),
                BigDecimal::add
        );
        return entry;
    }

    @Override
    public Optional<RealizedPnlEntry> findByEntryId(UUID entryId) {
        return Optional.ofNullable(entriesById.get(entryId));
    }

    @Override
    public Optional<RealizedPnlEntry> findByTradeId(UUID tradeId) {
        UUID entryId = entryIdsByTradeId.get(tradeId);
        if (entryId == null) {
            return Optional.empty();
        }
        return findByEntryId(entryId);
    }

    @Override
    public BigDecimal currentRealizedPnl(String portfolioId) {
        return currentRealizedPnlByPortfolioId.getOrDefault(portfolioId, BigDecimal.ZERO);
    }
}
