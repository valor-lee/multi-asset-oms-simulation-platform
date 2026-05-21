package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.Trade;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTradeRepository implements TradeRepository {

    private final Map<UUID, Trade> tradesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tradeIdsByOrderId = new ConcurrentHashMap<>();

    @Override
    public Trade save(Trade trade) {
        tradesById.put(trade.tradeId(), trade);
        tradeIdsByOrderId.put(trade.orderId(), trade.tradeId());
        return trade;
    }

    @Override
    public Optional<Trade> findByTradeId(UUID tradeId) {
        return Optional.ofNullable(tradesById.get(tradeId));
    }

    @Override
    public Optional<Trade> findByOrderId(UUID orderId) {
        UUID tradeId = tradeIdsByOrderId.get(orderId);
        if (tradeId == null) {
            return Optional.empty();
        }
        return findByTradeId(tradeId);
    }
}
