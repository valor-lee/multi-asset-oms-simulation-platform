package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.CashLedgerRepository;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCashLedgerRepository implements CashLedgerRepository {

    private final Map<UUID, CashLedgerEntry> entriesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entryIdsByTradeId = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> currentCashByPortfolioId = new ConcurrentHashMap<>();

    @Override
    public CashLedgerEntry save(CashLedgerEntry entry) {
        entriesById.put(entry.entryId(), entry);
        entryIdsByTradeId.put(entry.tradeId(), entry.entryId());
        currentCashByPortfolioId.merge(entry.portfolioId(), entry.cashDelta(), BigDecimal::add);
        return entry;
    }

    @Override
    public Optional<CashLedgerEntry> findByEntryId(UUID entryId) {
        return Optional.ofNullable(entriesById.get(entryId));
    }

    @Override
    public Optional<CashLedgerEntry> findByTradeId(UUID tradeId) {
        UUID entryId = entryIdsByTradeId.get(tradeId);
        if (entryId == null) {
            return Optional.empty();
        }
        return findByEntryId(entryId);
    }

    @Override
    public BigDecimal currentCash(String portfolioId) {
        return currentCashByPortfolioId.getOrDefault(portfolioId, BigDecimal.ZERO);
    }
}
