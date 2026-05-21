package com.multiassetoms.posttrade.infrastructure;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTradeRepositoryTest {

    private final InMemoryTradeRepository repository = new InMemoryTradeRepository();

    @Test
    void savesAndFindsTradeByTradeId() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000006001"),
                UUID.fromString("00000000-0000-0000-0000-000000005001")
        );

        repository.save(trade);

        Optional<Trade> found = repository.findByTradeId(trade.tradeId());
        assertTrue(found.isPresent());
        assertEquals(trade, found.get());
    }

    @Test
    void savesAndFindsTradeByOrderId() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000006002"),
                UUID.fromString("00000000-0000-0000-0000-000000005002")
        );

        repository.save(trade);

        Optional<Trade> found = repository.findByOrderId(trade.orderId());
        assertTrue(found.isPresent());
        assertEquals(trade, found.get());
    }

    @Test
    void returnsEmptyWhenTradeDoesNotExist() {
        Optional<Trade> found = repository.findByOrderId(
                UUID.fromString("00000000-0000-0000-0000-000000005099")
        );

        assertTrue(found.isEmpty());
    }

    private Trade createTrade(UUID tradeId, UUID orderId) {
        Instant capturedAt = Instant.parse("2026-05-21T01:00:00Z");
        return new Trade(
                tradeId,
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000005101"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                new BigDecimal("10"),
                TradeStatus.CAPTURED,
                capturedAt,
                capturedAt
        );
    }
}
