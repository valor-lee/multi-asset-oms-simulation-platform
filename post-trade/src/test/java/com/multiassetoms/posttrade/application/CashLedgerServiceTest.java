package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryCashLedgerRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import com.multiassetoms.posttrade.model.CashLedgerException;
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

class CashLedgerServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-23T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryCashLedgerRepository cashLedgerRepository =
            new InMemoryCashLedgerRepository();
    private final CashLedgerService service = new CashLedgerService(
            tradeRepository,
            cashLedgerRepository,
            fixedClock
    );

    @Test
    void postsBuyTradeAsNegativeCashDelta() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009101"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(trade.tradeId(), entry.tradeId());
        assertEquals(OrderSide.BUY, entry.side());
        assertEquals(new BigDecimal("-550000"), entry.cashDelta());
        assertEquals(Instant.parse("2026-05-23T01:00:00Z"), entry.postedAt());
        assertEquals(new BigDecimal("-550000"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void postsBuyTradeFeeAsAdditionalCashOutflow() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009108"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(new BigDecimal("-550100"), entry.cashDelta());
        assertEquals(new BigDecimal("-550100"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void postsBuyTradeTaxAsAdditionalCashOutflow() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009110"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(new BigDecimal("-550130"), entry.cashDelta());
        assertEquals(new BigDecimal("-550130"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void postsSellTradeAsPositiveCashDelta() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009102"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("220000"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(OrderSide.SELL, entry.side());
        assertEquals(new BigDecimal("220000"), entry.cashDelta());
        assertEquals(new BigDecimal("220000"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void postsSellTradeFeeAsReducedCashInflow() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009109"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("220000"),
                new BigDecimal("80"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(new BigDecimal("219920"), entry.cashDelta());
        assertEquals(new BigDecimal("219920"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void postsSellTradeTaxAsReducedCashInflow() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009111"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("220000"),
                new BigDecimal("80"),
                new BigDecimal("25"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry entry = service.post(trade.tradeId());

        assertEquals(new BigDecimal("219895"), entry.cashDelta());
        assertEquals(new BigDecimal("219895"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void accumulatesCashByPortfolio() {
        Trade buyTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009103"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                TradeStatus.SETTLED
        );
        Trade sellTrade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009104"),
                OrderSide.SELL,
                new BigDecimal("3"),
                new BigDecimal("165000"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(buyTrade);
        tradeRepository.save(sellTrade);

        service.post(buyTrade.tradeId());
        service.post(sellTrade.tradeId());

        assertEquals(new BigDecimal("-385000"), service.currentCash(buyTrade.portfolioId()));
    }

    @Test
    void returnsExistingLedgerEntryWhenPostIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009105"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerEntry firstResult = service.post(trade.tradeId());
        CashLedgerEntry secondResult = service.post(trade.tradeId());

        assertEquals(firstResult, secondResult);
        assertEquals(new BigDecimal("-550000"), service.currentCash(trade.portfolioId()));
    }

    @Test
    void rejectsNonSettledTrade() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009106"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550000"),
                TradeStatus.SETTLEMENT_PENDING
        );
        tradeRepository.save(trade);

        CashLedgerException exception = assertThrows(
                CashLedgerException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("only SETTLED trades can be posted to cash ledger", exception.getMessage());
    }

    @Test
    void rejectsSettledTradeWithoutGrossNotional() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000009107"),
                OrderSide.BUY,
                new BigDecimal("10"),
                null,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        CashLedgerException exception = assertThrows(
                CashLedgerException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("grossNotional is required to post cash ledger", exception.getMessage());
    }

    @Test
    void rejectsMissingTradeId() {
        CashLedgerException exception = assertThrows(
                CashLedgerException.class,
                () -> service.post(UUID.fromString("00000000-0000-0000-0000-000000009199"))
        );

        assertEquals("trade not found", exception.getMessage());
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal grossNotional,
            TradeStatus status
    ) {
        return createTrade(tradeId, side, quantity, grossNotional, null, null, status);
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal grossNotional,
            BigDecimal feeAmount,
            TradeStatus status
    ) {
        return createTrade(tradeId, side, quantity, grossNotional, feeAmount, null, status);
    }

    private Trade createTrade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
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
                grossNotional == null ? null : grossNotional.divide(quantity),
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
