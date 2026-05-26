package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryCashLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryPositionLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.LedgerPostingException;
import com.multiassetoms.posttrade.model.LedgerPostingResult;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostSettlementLedgerServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-25T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryPositionLedgerRepository positionLedgerRepository =
            new InMemoryPositionLedgerRepository();
    private final InMemoryCashLedgerRepository cashLedgerRepository =
            new InMemoryCashLedgerRepository();
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
    private final PostSettlementLedgerService service = new PostSettlementLedgerService(
            tradeRepository,
            positionLedgerService,
            cashLedgerService
    );

    @Test
    void postsSettledBuyTradeToPositionAndCashLedgers() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000012001"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        LedgerPostingResult result = service.post(trade.tradeId());

        assertEquals(trade.tradeId(), result.positionLedgerEntry().tradeId());
        assertEquals(trade.tradeId(), result.cashLedgerEntry().tradeId());
        assertEquals(new BigDecimal("10"), result.positionLedgerEntry().quantityDelta());
        assertEquals(new BigDecimal("-550100"), result.cashLedgerEntry().cashDelta());
        assertEquals(new BigDecimal("10"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(new BigDecimal("-550100"), cashLedgerService.currentCash("portfolio-1"));
    }

    @Test
    void postsSettledSellTradeToPositionAndCashLedgers() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000012002"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("220000"),
                new BigDecimal("80"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        LedgerPostingResult result = service.post(trade.tradeId());

        assertEquals(new BigDecimal("-4"), result.positionLedgerEntry().quantityDelta());
        assertEquals(new BigDecimal("219920"), result.cashLedgerEntry().cashDelta());
        assertEquals(new BigDecimal("-4"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(new BigDecimal("219920"), cashLedgerService.currentCash("portfolio-1"));
    }

    @Test
    void returnsExistingEntriesWhenPostIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000012003"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        LedgerPostingResult firstResult = service.post(trade.tradeId());
        LedgerPostingResult secondResult = service.post(trade.tradeId());

        assertEquals(firstResult, secondResult);
        assertEquals(new BigDecimal("10"), positionLedgerService.currentPosition("portfolio-1", "005930"));
        assertEquals(new BigDecimal("-550000"), cashLedgerService.currentCash("portfolio-1"));
    }

    @Test
    void rejectsNonSettledTradeBeforePostingAnyLedger() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000012004"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                null,
                TradeStatus.SETTLEMENT_PENDING
        );
        tradeRepository.save(trade);

        LedgerPostingException exception = assertThrows(
                LedgerPostingException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("only SETTLED trades can be posted to ledgers", exception.getMessage());
        assertTrue(positionLedgerRepository.findByTradeId(trade.tradeId()).isEmpty());
        assertTrue(cashLedgerRepository.findByTradeId(trade.tradeId()).isEmpty());
    }

    @Test
    void rejectsTradeWithoutGrossNotionalBeforePostingAnyLedger() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000012005"),
                OrderSide.BUY,
                new BigDecimal("10"),
                null,
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        LedgerPostingException exception = assertThrows(
                LedgerPostingException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("grossNotional is required to post ledgers", exception.getMessage());
        assertTrue(positionLedgerRepository.findByTradeId(trade.tradeId()).isEmpty());
        assertTrue(cashLedgerRepository.findByTradeId(trade.tradeId()).isEmpty());
    }

    @Test
    void rejectsMissingTradeId() {
        LedgerPostingException exception = assertThrows(
                LedgerPostingException.class,
                () -> service.post(UUID.fromString("00000000-0000-0000-0000-000000012099"))
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
                grossNotional == null ? null : grossNotional.divide(quantity),
                grossNotional,
                feeAmount,
                status,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
