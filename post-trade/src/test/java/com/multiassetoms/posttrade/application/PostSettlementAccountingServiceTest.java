package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryAverageCostRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryCashLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryPositionLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryRealizedPnlRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.AccountingPostingResult;
import com.multiassetoms.posttrade.model.LedgerPostingException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostSettlementAccountingServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-23T00:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryPositionLedgerRepository positionLedgerRepository =
            new InMemoryPositionLedgerRepository();
    private final InMemoryCashLedgerRepository cashLedgerRepository =
            new InMemoryCashLedgerRepository();
    private final InMemoryAverageCostRepository averageCostRepository =
            new InMemoryAverageCostRepository();
    private final InMemoryRealizedPnlRepository realizedPnlRepository =
            new InMemoryRealizedPnlRepository();
    private final PositionLedgerService positionLedgerService = new PositionLedgerService(
            tradeRepository,
            positionLedgerRepository,
            fixedClock
    );
    private final CashLedgerService cashLedgerService = new CashLedgerService(
            tradeRepository,
            cashLedgerRepository,
            fixedClock
    );
    private final PostSettlementLedgerService postSettlementLedgerService = new PostSettlementLedgerService(
            tradeRepository,
            positionLedgerService,
            cashLedgerService
    );
    private final AverageCostService averageCostService = new AverageCostService(
            tradeRepository,
            averageCostRepository,
            fixedClock
    );
    private final RealizedPnlService realizedPnlService = new RealizedPnlService(
            tradeRepository,
            realizedPnlRepository,
            averageCostService,
            fixedClock
    );
    private final PostSettlementAccountingService service = new PostSettlementAccountingService(
            tradeRepository,
            postSettlementLedgerService,
            averageCostService,
            realizedPnlService
    );

    @Test
    void postsSettledBuyTradeToAccounting() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000080001"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        AccountingPostingResult result = service.post(trade.tradeId());

        assertEquals(trade.tradeId(), result.positionLedgerEntry().tradeId());
        assertEquals(trade.tradeId(), result.cashLedgerEntry().tradeId());
        assertEquals(trade.tradeId(), result.averageCostEntry().tradeId());
        assertNull(result.realizedPnlEntry());
        assertEquals(new BigDecimal("10"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(new BigDecimal("-550100"), cashLedgerService.currentCash("portfolio-1"));
        assertEquals(
                new BigDecimal("55010.0000000000"),
                averageCostService.currentAverageCost("portfolio-1", "005930").averageCost()
        );
    }

    @Test
    void postsSettledSellTradeToAccountingWithRealizedPnl() {
        Trade buyTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000080002"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("540000"),
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        Trade sellTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000080003"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("220000"),
                new BigDecimal("80"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(buyTrade);
        tradeRepository.save(sellTrade);
        service.post(buyTrade.tradeId());

        AccountingPostingResult result = service.post(sellTrade.tradeId());

        assertEquals(new BigDecimal("-4"), result.positionLedgerEntry().quantityDelta());
        assertEquals(new BigDecimal("219920"), result.cashLedgerEntry().cashDelta());
        assertEquals(new BigDecimal("-216000.0000000000"), result.averageCostEntry().costDelta());
        assertEquals(new BigDecimal("3920.0000000000"), result.realizedPnlEntry().realizedPnl());
        assertEquals(new BigDecimal("6"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(
                new BigDecimal("54000.0000000000"),
                averageCostService.currentAverageCost("portfolio-1", "005930").averageCost()
        );
    }

    @Test
    void returnsExistingEntriesWhenPostIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000080004"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        AccountingPostingResult firstResult = service.post(trade.tradeId());
        AccountingPostingResult secondResult = service.post(trade.tradeId());

        assertEquals(firstResult, secondResult);
        assertEquals(new BigDecimal("10"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(new BigDecimal("-550000"), cashLedgerService.currentCash("portfolio-1"));
    }

    @Test
    void rejectsNonSettledTradeBeforePostingAccounting() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000080005"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                TradeStatus.SETTLEMENT_PENDING
        );
        tradeRepository.save(trade);

        LedgerPostingException exception = assertThrows(
                LedgerPostingException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("only SETTLED trades can be posted to accounting", exception.getMessage());
    }

    @Test
    void rejectsMissingTradeId() {
        TradeNotFoundException exception = assertThrows(
                TradeNotFoundException.class,
                () -> service.post(UUID.fromString("00000000-0000-0000-0000-000000080099"))
        );

        assertEquals("trade not found", exception.getMessage());
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal grossNotional,
            BigDecimal feeAmount,
            TradeStatus status
    ) {
        Instant capturedAt = Instant.parse("2026-06-22T00:00:00Z");
        Instant settledAt = status == TradeStatus.SETTLED
                ? Instant.parse("2026-06-23T00:00:00Z")
                : null;
        return new Trade(
                tradeId,
                UUID.fromString("00000000-0000-0000-0000-000000081001"),
                UUID.fromString("00000000-0000-0000-0000-000000082001"),
                "portfolio-1",
                "005930",
                side,
                quantity,
                grossNotional == null ? null : grossNotional.divide(quantity),
                grossNotional,
                feeAmount,
                null,
                status,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
