package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemoryAverageCostRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostException;
import com.multiassetoms.posttrade.model.CurrentAverageCost;
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

class AverageCostServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-21T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemoryAverageCostRepository averageCostRepository = new InMemoryAverageCostRepository();
    private final AverageCostService service = new AverageCostService(
            tradeRepository,
            averageCostRepository,
            fixedClock
    );

    @Test
    void postsBuyTradeToAverageCost() {
        Trade trade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075001"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        AverageCostEntry entry = service.post(trade.tradeId());

        assertEquals(trade.tradeId(), entry.tradeId());
        assertEquals(new BigDecimal("550100"), entry.costDelta());
        assertEquals(new BigDecimal("10"), entry.positionQuantity());
        assertEquals(new BigDecimal("550100"), entry.costBasis());
        assertEquals(new BigDecimal("55010.0000000000"), entry.averageCost());
    }

    @Test
    void recalculatesAverageCostAfterAdditionalBuy() {
        Trade firstTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075002"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        Trade secondTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075003"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("57000"),
                new BigDecimal("570000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(firstTrade);
        tradeRepository.save(secondTrade);

        service.post(firstTrade.tradeId());
        AverageCostEntry entry = service.post(secondTrade.tradeId());

        assertEquals(new BigDecimal("20"), entry.positionQuantity());
        assertEquals(new BigDecimal("1120000"), entry.costBasis());
        assertEquals(new BigDecimal("56000.0000000000"), entry.averageCost());
    }

    @Test
    void reducesCostBasisAtCurrentAverageCostForSell() {
        Trade buyTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075004"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        Trade sellTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075005"),
                OrderSide.SELL,
                new BigDecimal("4"),
                new BigDecimal("60000"),
                new BigDecimal("240000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(buyTrade);
        tradeRepository.save(sellTrade);

        service.post(buyTrade.tradeId());
        AverageCostEntry entry = service.post(sellTrade.tradeId());

        assertEquals(new BigDecimal("-220000.0000000000"), entry.costDelta());
        assertEquals(new BigDecimal("6"), entry.positionQuantity());
        assertEquals(new BigDecimal("330000.0000000000"), entry.costBasis());
        assertEquals(new BigDecimal("55000.0000000000"), entry.averageCost());
    }

    @Test
    void clearsCostBasisWhenPositionIsFullySold() {
        Trade buyTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075006"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        Trade sellTrade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075007"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("60000"),
                new BigDecimal("600000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(buyTrade);
        tradeRepository.save(sellTrade);

        service.post(buyTrade.tradeId());
        AverageCostEntry entry = service.post(sellTrade.tradeId());

        assertEquals(BigDecimal.ZERO, entry.positionQuantity());
        assertEquals(BigDecimal.ZERO, entry.costBasis());
        assertEquals(BigDecimal.ZERO, entry.averageCost());
    }

    @Test
    void returnsExistingEntryWhenTradeAlreadyPosted() {
        Trade trade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075008"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        AverageCostEntry firstEntry = service.post(trade.tradeId());
        AverageCostEntry secondEntry = service.post(trade.tradeId());

        assertEquals(firstEntry, secondEntry);
    }

    @Test
    void rejectsNonSettledTrade() {
        Trade trade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075009"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.CAPTURED
        );
        tradeRepository.save(trade);

        AverageCostException exception = assertThrows(
                AverageCostException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("only SETTLED trades can be posted to average cost", exception.getMessage());
    }

    @Test
    void rejectsSellThatExceedsCurrentPosition() {
        Trade trade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075010"),
                OrderSide.SELL,
                new BigDecimal("10"),
                new BigDecimal("60000"),
                new BigDecimal("600000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);

        AverageCostException exception = assertThrows(
                AverageCostException.class,
                () -> service.post(trade.tradeId())
        );

        assertEquals("sell quantity exceeds current position", exception.getMessage());
    }

    @Test
    void getsCurrentAverageCost() {
        Trade trade = trade(
                UUID.fromString("00000000-0000-0000-0000-000000075011"),
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                new BigDecimal("550000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                TradeStatus.SETTLED
        );
        tradeRepository.save(trade);
        service.post(trade.tradeId());

        CurrentAverageCost currentAverageCost = service.currentAverageCost("portfolio-1", "005930");

        assertEquals(new BigDecimal("10"), currentAverageCost.quantity());
        assertEquals(new BigDecimal("550000"), currentAverageCost.costBasis());
        assertEquals(new BigDecimal("55000.0000000000"), currentAverageCost.averageCost());
    }

    private Trade trade(
            UUID tradeId,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal averageFillPrice,
            BigDecimal grossNotional,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            TradeStatus status
    ) {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        return new Trade(
                tradeId,
                UUID.fromString("00000000-0000-0000-0000-000000076001"),
                UUID.fromString("00000000-0000-0000-0000-000000077001"),
                "portfolio-1",
                "005930",
                side,
                quantity,
                averageFillPrice,
                grossNotional,
                feeAmount,
                taxAmount,
                status,
                now,
                now,
                now
        );
    }
}
