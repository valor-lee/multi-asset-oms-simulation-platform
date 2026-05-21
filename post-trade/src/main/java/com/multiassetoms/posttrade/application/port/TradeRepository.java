package com.multiassetoms.posttrade.application.port;

import com.multiassetoms.posttrade.model.Trade;

import java.util.Optional;
import java.util.UUID;

public interface TradeRepository {

    Trade save(Trade trade);

    Optional<Trade> findByTradeId(UUID tradeId);

    Optional<Trade> findByOrderId(UUID orderId);
}
