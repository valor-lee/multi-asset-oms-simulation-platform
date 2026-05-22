package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.SettlementRepository;
import com.multiassetoms.posttrade.model.Settlement;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySettlementRepository implements SettlementRepository {

    private final Map<UUID, Settlement> settlementsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> settlementIdsByTradeId = new ConcurrentHashMap<>();

    @Override
    public Settlement save(Settlement settlement) {
        settlementsById.put(settlement.settlementId(), settlement);
        settlementIdsByTradeId.put(settlement.tradeId(), settlement.settlementId());
        return settlement;
    }

    @Override
    public Optional<Settlement> findBySettlementId(UUID settlementId) {
        return Optional.ofNullable(settlementsById.get(settlementId));
    }

    @Override
    public Optional<Settlement> findByTradeId(UUID tradeId) {
        UUID settlementId = settlementIdsByTradeId.get(tradeId);
        if (settlementId == null) {
            return Optional.empty();
        }
        return findBySettlementId(settlementId);
    }
}
