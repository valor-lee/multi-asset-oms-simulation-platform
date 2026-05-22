package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.Settlement;

import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findBySettlementId(UUID settlementId);

    Optional<Settlement> findByTradeId(UUID tradeId);
}
