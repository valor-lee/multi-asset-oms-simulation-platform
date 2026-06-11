package com.multiassetoms.posttrade.application;

import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.infrastructure.InMemoryMarketPriceRepository;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryPositionLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import com.multiassetoms.posttrade.model.UnrealizedPnlException;
import com.multiassetoms.posttrade.model.UnrealizedPnlSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnrealizedPnlServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-27T02:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryPositionLedgerRepository positionLedgerRepository =
            new InMemoryPositionLedgerRepository();
    private final InMemoryMarketPriceRepository marketPriceRepository =
            new InMemoryMarketPriceRepository();
    private final MarketPriceService marketPriceService = new MarketPriceService(
            marketPriceRepository,
            fixedClock
    );
    private final PositionLedgerService positionLedgerService = new PositionLedgerService(
            tradeRepository,
            positionLedgerRepository,
            fixedClock
    );
    private final UnrealizedPnlService service = new UnrealizedPnlService(
            positionLedgerService,
            marketPriceService,
            fixedClock
    );

    @Test
    void calculatesPositiveUnrealizedPnl() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000014001"),
                OrderSide.BUY,
                new BigDecimal("10")
        );
        tradeRepository.save(trade);
        positionLedgerService.post(trade.tradeId());

        UnrealizedPnlSnapshot snapshot = service.snapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("54000"),
                new BigDecimal("55000")
        );

        assertEquals("portfolio-1", snapshot.portfolioId());
        assertEquals("005930", snapshot.instrumentId());
        assertEquals(new BigDecimal("10"), snapshot.quantity());
        assertEquals(new BigDecimal("54000"), snapshot.averageCost());
        assertEquals(new BigDecimal("55000"), snapshot.marketPrice());
        assertEquals(new BigDecimal("540000"), snapshot.costBasis());
        assertEquals(new BigDecimal("550000"), snapshot.marketValue());
        assertEquals(new BigDecimal("10000"), snapshot.unrealizedPnl());
        assertEquals(Instant.parse("2026-05-27T02:00:00Z"), snapshot.valuedAt());
    }

    @Test
    void calculatesNegativeUnrealizedPnl() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000014002"),
                OrderSide.BUY,
                new BigDecimal("10")
        );
        tradeRepository.save(trade);
        positionLedgerService.post(trade.tradeId());

        UnrealizedPnlSnapshot snapshot = service.snapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("54000"),
                new BigDecimal("53000")
        );

        assertEquals(new BigDecimal("-10000"), snapshot.unrealizedPnl());
    }

    @Test
    void calculatesUnrealizedPnlWithLatestMarketPrice() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000014004"),
                OrderSide.BUY,
                new BigDecimal("10")
        );
        tradeRepository.save(trade);
        positionLedgerService.post(trade.tradeId());
        marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-05-27T01:59:00Z")
        );

        UnrealizedPnlSnapshot snapshot = service.snapshotWithLatestMarketPrice(
                "portfolio-1",
                "005930",
                new BigDecimal("54000")
        );

        assertEquals(new BigDecimal("55000"), snapshot.marketPrice());
        assertEquals(new BigDecimal("10000"), snapshot.unrealizedPnl());
    }

    @Test
    void rejectsMissingLatestMarketPrice() {
        MarketPriceNotFoundException exception = assertThrows(
                MarketPriceNotFoundException.class,
                () -> service.snapshotWithLatestMarketPrice(
                        "portfolio-1",
                        "005930",
                        new BigDecimal("54000")
                )
        );

        assertEquals("market price not found", exception.getMessage());
    }

    @Test
    void returnsZeroSnapshotWhenPositionDoesNotExist() {
        UnrealizedPnlSnapshot snapshot = service.snapshot(
                "portfolio-1",
                "000660",
                new BigDecimal("100000"),
                new BigDecimal("110000")
        );

        assertEquals(BigDecimal.ZERO, snapshot.quantity());
        assertEquals(BigDecimal.ZERO, snapshot.costBasis());
        assertEquals(BigDecimal.ZERO, snapshot.marketValue());
        assertEquals(BigDecimal.ZERO, snapshot.unrealizedPnl());
    }

    @Test
    void rejectsShortPosition() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000014003"),
                OrderSide.SELL,
                new BigDecimal("4")
        );
        tradeRepository.save(trade);
        positionLedgerService.post(trade.tradeId());

        UnrealizedPnlException exception = assertThrows(
                UnrealizedPnlException.class,
                () -> service.snapshot(
                        "portfolio-1",
                        "005930",
                        new BigDecimal("54000"),
                        new BigDecimal("55000")
                )
        );

        assertEquals("short positions are not supported for unrealized PnL", exception.getMessage());
    }

    @Test
    void rejectsMissingPortfolioId() {
        UnrealizedPnlException exception = assertThrows(
                UnrealizedPnlException.class,
                () -> service.snapshot(null, "005930", BigDecimal.ZERO, BigDecimal.ZERO)
        );

        assertEquals("portfolioId is required", exception.getMessage());
    }

    @Test
    void rejectsMissingInstrumentId() {
        UnrealizedPnlException exception = assertThrows(
                UnrealizedPnlException.class,
                () -> service.snapshot("portfolio-1", " ", BigDecimal.ZERO, BigDecimal.ZERO)
        );

        assertEquals("instrumentId is required", exception.getMessage());
    }

    @Test
    void rejectsNegativeAverageCost() {
        UnrealizedPnlException exception = assertThrows(
                UnrealizedPnlException.class,
                () -> service.snapshot(
                        "portfolio-1",
                        "005930",
                        new BigDecimal("-1"),
                        BigDecimal.ZERO
                )
        );

        assertEquals("averageCost must be zero or greater", exception.getMessage());
    }

    @Test
    void rejectsNegativeMarketPrice() {
        UnrealizedPnlException exception = assertThrows(
                UnrealizedPnlException.class,
                () -> service.snapshot(
                        "portfolio-1",
                        "005930",
                        BigDecimal.ZERO,
                        new BigDecimal("-1")
                )
        );

        assertEquals("marketPrice must be zero or greater", exception.getMessage());
    }

    private Trade createTrade(UUID tradeId, OrderSide side, BigDecimal quantity) {
        Instant capturedAt = Instant.parse("2026-05-21T01:00:00Z");
        Instant settledAt = Instant.parse("2026-05-22T01:00:00Z");
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
                null,
                null,
                TradeStatus.SETTLED,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
