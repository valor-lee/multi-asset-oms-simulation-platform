package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryRealizedPnlRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import com.multiassetoms.posttrade.model.RealizedPnlException;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealizedPnlServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-27T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryRealizedPnlRepository realizedPnlRepository =
            new InMemoryRealizedPnlRepository();
    private final RealizedPnlService service = new RealizedPnlService(
            tradeRepository,
            realizedPnlRepository,
            fixedClock
    );

    @Test
    void postsSellTradeRealizedPnl() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009201"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlEntry entry = service.post(trade.tradeId(), new BigDecimal("54000"));

        assertEquals(trade.tradeId(), entry.tradeId());
        assertEquals(new BigDecimal("10"), entry.quantity());
        assertEquals(new BigDecimal("55000.0000000000"), entry.averageSellPrice());
        assertEquals(new BigDecimal("54000"), entry.averageCost());
        assertEquals(new BigDecimal("550000"), entry.grossNotional());
        assertEquals(new BigDecimal("100"), entry.feeAmount());
        assertEquals(new BigDecimal("30"), entry.taxAmount());
        assertEquals(new BigDecimal("9870"), entry.realizedPnl());
        assertEquals(Instant.parse("2026-05-27T01:00:00Z"), entry.postedAt());
        assertEquals(new BigDecimal("9870"), service.currentRealizedPnl(trade.portfolioId()));
    }

    @Test
    void postsNegativeRealizedPnlWhenSellPriceIsBelowAverageCost() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009202"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("53000.0000000000"),
                new BigDecimal("530000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlEntry entry = service.post(trade.tradeId(), new BigDecimal("54000"));

        assertEquals(new BigDecimal("-10130"), entry.realizedPnl());
        assertEquals(new BigDecimal("-10130"), service.currentRealizedPnl(trade.portfolioId()));
    }

    @Test
    void treatsMissingFeeAndTaxAsZero() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009203"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlEntry entry = service.post(trade.tradeId(), new BigDecimal("54000"));

        assertEquals(BigDecimal.ZERO, entry.feeAmount());
        assertEquals(BigDecimal.ZERO, entry.taxAmount());
        assertEquals(new BigDecimal("10000"), entry.realizedPnl());
    }

    @Test
    void returnsExistingEntryWhenPostIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009204"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlEntry firstResult = service.post(trade.tradeId(), new BigDecimal("54000"));
        RealizedPnlEntry secondResult = service.post(trade.tradeId(), new BigDecimal("52000"));

        assertEquals(firstResult, secondResult);
        assertEquals(new BigDecimal("10000"), service.currentRealizedPnl(trade.portfolioId()));
    }

    @Test
    void rejectsBuyTrade() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009205"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlException exception = assertThrows(
                RealizedPnlException.class,
                () -> service.post(trade.tradeId(), new BigDecimal("54000"))
        );

        assertEquals("only SELL trades can produce realized PnL", exception.getMessage());
    }

    @Test
    void rejectsNonSettledTrade() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009206"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLEMENT_PENDING
        );
        tradeRepository.save(trade);

        RealizedPnlException exception = assertThrows(
                RealizedPnlException.class,
                () -> service.post(trade.tradeId(), new BigDecimal("54000"))
        );

        assertEquals("only SETTLED trades can be posted to realized PnL", exception.getMessage());
    }

    @Test
    void rejectsMissingAverageFillPrice() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009207"),
                OrderSide.SELL,
                new BigDecimal("10"),
                null,
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlException exception = assertThrows(
                RealizedPnlException.class,
                () -> service.post(trade.tradeId(), new BigDecimal("54000"))
        );

        assertEquals("averageFillPrice is required to post realized PnL", exception.getMessage());
    }

    @Test
    void rejectsMissingGrossNotional() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009208"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                null,
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlException exception = assertThrows(
                RealizedPnlException.class,
                () -> service.post(trade.tradeId(), new BigDecimal("54000"))
        );

        assertEquals("grossNotional is required to post realized PnL", exception.getMessage());
    }

    @Test
    void rejectsNegativeAverageCost() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009209"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        RealizedPnlException exception = assertThrows(
                RealizedPnlException.class,
                () -> service.post(trade.tradeId(), new BigDecimal("-1"))
        );

        assertEquals("averageCost must be zero or greater", exception.getMessage());
    }

    @Test
    void rejectsMissingTradeId() {
        TradeNotFoundException exception = assertThrows(
                TradeNotFoundException.class,
                () -> service.post(
                        UUID.fromString("00000000-0000-0000-0000-000000009299"),
                        new BigDecimal("54000")
                )
        );

        assertEquals("trade not found", exception.getMessage());
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal averageFillPrice,
            BigDecimal grossNotional,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
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
                averageFillPrice,
                grossNotional,
                feeAmount,
                taxAmount,
                status,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
