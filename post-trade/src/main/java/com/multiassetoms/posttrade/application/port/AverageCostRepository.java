package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostSnapshot;
import com.multiassetoms.posttrade.model.PositionKey;

import java.util.Optional;
import java.util.UUID;

public interface AverageCostRepository {

    AverageCostEntry save(AverageCostEntry entry);

    Optional<AverageCostEntry> findByEntryId(UUID entryId);

    Optional<AverageCostEntry> findByTradeId(UUID tradeId);

    AverageCostSnapshot currentAverageCost(PositionKey positionKey);
}
