package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.infrastructure.InMemorySettlementRepository;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.Settlement;
import com.multiassetoms.posttrade.model.SettlementException;
import com.multiassetoms.posttrade.model.SettlementNotFoundException;
import com.multiassetoms.posttrade.model.SettlementStatus;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-22T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final InMemorySettlementRepository settlementRepository = new InMemorySettlementRepository();
    private final SettlementService service = new SettlementService(
            tradeRepository,
            settlementRepository,
            fixedClock
    );

    @Test
    void schedulesCapturedTradeForSettlement() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007001"),
                TradeStatus.CAPTURED,
                null
        );
        tradeRepository.save(trade);

        Settlement settlement = service.scheduleSettlement(
                trade.tradeId(),
                LocalDate.parse("2026-05-24")
        );

        Trade updatedTrade = tradeRepository.findByTradeId(trade.tradeId()).orElseThrow();
        assertEquals(trade.tradeId(), settlement.tradeId());
        assertEquals(LocalDate.parse("2026-05-24"), settlement.settlementDate());
        assertEquals(SettlementStatus.PENDING, settlement.status());
        assertEquals(Instant.parse("2026-05-22T01:00:00Z"), settlement.createdAt());
        assertNull(settlement.settledAt());
        assertEquals(TradeStatus.SETTLEMENT_PENDING, updatedTrade.status());
        assertNull(updatedTrade.settledAt());
        assertEquals(Instant.parse("2026-05-22T01:00:00Z"), updatedTrade.updatedAt());
    }

    @Test
    void returnsExistingSettlementWhenScheduleIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007002"),
                TradeStatus.CAPTURED,
                null
        );
        tradeRepository.save(trade);
        Settlement firstResult = service.scheduleSettlement(
                trade.tradeId(),
                LocalDate.parse("2026-05-24")
        );

        Settlement secondResult = service.scheduleSettlement(
                trade.tradeId(),
                LocalDate.parse("2026-05-25")
        );

        assertEquals(firstResult, secondResult);
        assertEquals(LocalDate.parse("2026-05-24"), secondResult.settlementDate());
    }

    @Test
    void confirmsPendingSettlement() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007003"),
                TradeStatus.CAPTURED,
                null
        );
        tradeRepository.save(trade);
        Settlement pendingSettlement = service.scheduleSettlement(
                trade.tradeId(),
                LocalDate.parse("2026-05-24")
        );

        Settlement settledSettlement = service.confirmSettlement(pendingSettlement.settlementId());

        Trade settledTrade = tradeRepository.findByTradeId(trade.tradeId()).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settledSettlement.status());
        assertEquals(Instant.parse("2026-05-22T01:00:00Z"), settledSettlement.settledAt());
        assertEquals(TradeStatus.SETTLED, settledTrade.status());
        assertEquals(Instant.parse("2026-05-22T01:00:00Z"), settledTrade.settledAt());
    }

    @Test
    void returnsSettledSettlementWhenConfirmIsRequestedAgain() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007004"),
                TradeStatus.CAPTURED,
                null
        );
        tradeRepository.save(trade);
        Settlement pendingSettlement = service.scheduleSettlement(
                trade.tradeId(),
                LocalDate.parse("2026-05-24")
        );
        Settlement firstResult = service.confirmSettlement(pendingSettlement.settlementId());

        Settlement secondResult = service.confirmSettlement(firstResult.settlementId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.settledAt(), secondResult.settledAt());
    }

    @Test
    void rejectsScheduleForNonCapturedTrade() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007005"),
                TradeStatus.SETTLEMENT_PENDING,
                null
        );
        tradeRepository.save(trade);

        SettlementException exception = assertThrows(
                SettlementException.class,
                () -> service.scheduleSettlement(trade.tradeId(), LocalDate.parse("2026-05-24"))
        );

        assertEquals("only CAPTURED trades can be scheduled for settlement", exception.getMessage());
    }

    @Test
    void rejectsScheduleWithoutSettlementDate() {
        Trade trade = createTrade(
                UUID.fromString("00000000-0000-0000-0000-000000007006"),
                TradeStatus.CAPTURED,
                null
        );
        tradeRepository.save(trade);

        SettlementException exception = assertThrows(
                SettlementException.class,
                () -> service.scheduleSettlement(trade.tradeId(), null)
        );

        assertEquals("settlementDate is required", exception.getMessage());
    }

    @Test
    void rejectsMissingTradeId() {
        TradeNotFoundException exception = assertThrows(
                TradeNotFoundException.class,
                () -> service.scheduleSettlement(
                        UUID.fromString("00000000-0000-0000-0000-000000007099"),
                        LocalDate.parse("2026-05-24")
                )
        );

        assertEquals("trade not found", exception.getMessage());
    }

    @Test
    void rejectsMissingSettlementId() {
        SettlementNotFoundException exception = assertThrows(
                SettlementNotFoundException.class,
                () -> service.confirmSettlement(
                        UUID.fromString("00000000-0000-0000-0000-000000008099")
                )
        );

        assertEquals("settlement not found", exception.getMessage());
    }

    private Trade createTrade(UUID tradeId, TradeStatus status, Instant settledAt) {
        Instant capturedAt = Instant.parse("2026-05-21T01:00:00Z");
        return new Trade(
                tradeId,
                UUID.fromString("00000000-0000-0000-0000-000000005001"),
                UUID.fromString("00000000-0000-0000-0000-000000005101"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                null,
                null,
                status,
                capturedAt,
                settledAt,
                capturedAt
        );
    }
}
