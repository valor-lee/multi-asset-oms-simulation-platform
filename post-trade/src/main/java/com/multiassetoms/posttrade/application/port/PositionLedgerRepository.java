package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.PositionKey;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PositionLedgerRepository {

    PositionLedgerEntry save(PositionLedgerEntry entry);

    Optional<PositionLedgerEntry> findByEntryId(UUID entryId);

    Optional<PositionLedgerEntry> findByTradeId(UUID tradeId);

    BigDecimal currentPosition(PositionKey positionKey);
}
