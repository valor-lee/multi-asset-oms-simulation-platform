package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.RealizedPnlEntry;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface RealizedPnlRepository {

    RealizedPnlEntry save(RealizedPnlEntry entry);

    Optional<RealizedPnlEntry> findByEntryId(UUID entryId);

    Optional<RealizedPnlEntry> findByTradeId(UUID tradeId);

    BigDecimal currentRealizedPnl(String portfolioId);
}
