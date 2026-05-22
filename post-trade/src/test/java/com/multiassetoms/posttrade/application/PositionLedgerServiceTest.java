package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryPositionLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
import com.multiassetoms.posttrade.model.PositionLedgerException;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionLedgerServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-22T02:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryPositionLedgerRepository positionLedgerRepository =
            new InMemoryPositionLedgerRepository();
    private final PositionLedgerService service = new PositionLedgerService(
            tradeRepository,
            positionLedgerRepository,
            fixedClock
    );

    @Test
    void postsBuyTradeAsPositivePositionDelta() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                OrderSide.BUY,
                new BigDecimal("10"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        PositionLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(trade.tradeId(), entry.tradeId());
        assertEquals(OrderSide.BUY, entry.side());
        assertEquals(new BigDecimal("10"), entry.quantityDelta());
        assertEquals(Instant.parse("2026-05-22T02:00:00Z"), entry.postedAt());
        assertEquals(
                new BigDecimal("10"),
                service.currentPosition(trade.portfolioId(), trade.instrumentId())
        );
    }

    @Test
    void postsSellTradeAsNegativePositionDelta() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                OrderSide.SELL,
                new BigDecimal("4"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        PositionLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(OrderSide.SELL, entry.side());
        assertEquals(new BigDecimal("-4"), entry.quantityDelta());
        assertEquals(
                new BigDecimal("-4"),
                service.currentPosition(trade.portfolioId(), trade.instrumentId())
        );
    }

    @Test
    void accumulatesPositionByPortfolioAndInstrument() {
        Trade buyTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009003"),
                OrderSide.BUY,
                new BigDecimal("10"),
                TradeStatus.SETTLED
        );
        Trade sellTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009004"),
                OrderSide.SELL,
                new BigDecimal("3"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(buyTrade);
        tradeRepository.save(sellTrade);

        service.post(buyTrade.tradeId());
        service.post(sellTrade.tradeId());

        assertEquals(
                new BigDecimal("7"),
                service.currentPosition(buyTrade.portfolioId(), buyTrade.instrumentId())
        );
    }

    @Test
    void returnsExistingLedgerEntryWhenPostIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009005"),
                OrderSide.BUY,
                new BigDecimal("10"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        PositionLedgerEntry firstResult = service.post(trade.tradeId());
        PositionLedgerEntry secondResult = service.post(trade.tradeId());

        assertEquals(firstResult, secondResult);
        assertEquals(
                new BigDecimal("10"),
                service.currentPosition(trade.portfolioId(), trade.instrumentId())
        );
    }

    @Test
    void rejectsNonSettledTrade() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009006"),
                OrderSide.BUY,
                new BigDecimal("10"),
                TradeStatus.SETTLEMENT_PENDING
        );
        tradeRepository.save(trade);

        PositionLedgerException exception = assertThrows(
                PositionLedgerException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("only SETTLED trades can be posted to position ledger", exception.getMessage());
    }

    @Test
    void rejectsMissingTradeId() {
        PositionLedgerException exception = assertThrows(
                PositionLedgerException.class,
                () -> service.post(UUID.fromString("00000000-0000-0000-0000-000000009099"))
        );

        assertEquals("trade not found", exception.getMessage());
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            TradeStatus status
    ) {
        Instant capturedAt = Instant.parse("2026-05-21T01:00:00Z");
        Instant settledAt = status == TradeStatus.SETTLED
                ? Instant.parse("2026-05-22T01:00:00Z")
                : null;
        return new Trade(
                tradeId,
                UUID.fromString("00000000-0000-0000-0000-000000005001"),
                UUID.fromString("00000000-0000-0000-0000-000000005101"),
                "portfolio-1",
                "005930",
                side,
                quantity,
                new BigDecimal("55000.0000000000"),
                quantity.multiply(new BigDecimal("55000")),
                status,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
