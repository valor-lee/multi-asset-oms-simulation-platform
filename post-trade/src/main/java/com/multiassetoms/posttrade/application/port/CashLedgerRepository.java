package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.CashLedgerEntry;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CashLedgerRepository {

    CashLedgerEntry save(CashLedgerEntry entry);

    Optional<CashLedgerEntry> findByEntryId(UUID entryId);

    Optional<CashLedgerEntry> findByTradeId(UUID tradeId);

    BigDecimal currentCash(String portfolioId);
}
