package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.application.port.SettlementRepository;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.Settlement;
import com.multiassetoms.posttrade.model.SettlementException;
import com.multiassetoms.posttrade.model.SettlementStatus;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class SettlementService {

    private final TradeRepository tradeRepository;
    private final SettlementRepository settlementRepository;
    private final Clock clock;

    public SettlementService(
            TradeRepository tradeRepository,
            SettlementRepository settlementRepository,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.settlementRepository = settlementRepository;
        this.clock = clock;
    }

    /**
     * 캡처된 trade를 settlement 예정 상태로 전이한다.
     * 상태별 처리:
     * - CAPTURED: settlement를 생성하고 trade를 SETTLEMENT_PENDING으로 전이
     * - SETTLEMENT_PENDING: 중복 요청으로 보고 기존 settlement 반환
     * - 그 외 상태: settlement 예정 생성 대상이 아니므로 예외
     *
     * @param tradeId settlement 예정으로 전이할 trade id
     * @param settlementDate 예정 결제일
     * @return 생성되었거나 이미 존재하는 settlement
     */
    public Settlement scheduleSettlement(UUID tradeId, LocalDate settlementDate) {
        validateSettlementDate(settlementDate);

        Settlement existingSettlement = settlementRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingSettlement != null) {
            return existingSettlement;
        }

        Trade trade = findTrade(tradeId);
        validateCaptured(trade);

        Instant scheduledAt = Instant.now(clock);
        Settlement settlement = settlementRepository.save(new Settlement(
                UUID.randomUUID(),
                trade.tradeId(),
                settlementDate,
                SettlementStatus.PENDING,
                scheduledAt,
                null,
                scheduledAt
        ));
        tradeRepository.save(transitionTrade(trade, TradeStatus.SETTLEMENT_PENDING, null, scheduledAt));
        return settlement;
    }

    /**
     * 예정된 settlement를 완료 처리한다.
     * 상태별 처리:
     * - PENDING: settlement와 trade를 SETTLED로 전이
     * - SETTLED: 중복 요청으로 보고 기존 settlement 반환
     * - missing settlement: 예외
     *
     * @param settlementId 완료 처리할 settlement id
     * @return SETTLED 상태의 settlement
     */
    public Settlement confirmSettlement(UUID settlementId) {
        Settlement settlement = settlementRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new SettlementException("settlement not found"));

        if (settlement.status() == SettlementStatus.SETTLED) {
            return settlement;
        }

        Trade trade = findTrade(settlement.tradeId());
        validateSettlementPending(trade);

        Instant settledAt = Instant.now(clock);
        Settlement settledSettlement = settlementRepository.save(new Settlement(
                settlement.settlementId(),
                settlement.tradeId(),
                settlement.settlementDate(),
                SettlementStatus.SETTLED,
                settlement.createdAt(),
                settledAt,
                settledAt
        ));
        tradeRepository.save(transitionTrade(trade, TradeStatus.SETTLED, settledAt, settledAt));
        return settledSettlement;
    }

    private Trade findTrade(UUID tradeId) {
        return tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new SettlementException("trade not found"));
    }

    private void validateSettlementDate(LocalDate settlementDate) {
        if (settlementDate == null) {
            throw new SettlementException("settlementDate is required");
        }
    }

    private void validateCaptured(Trade trade) {
        if (trade.status() != TradeStatus.CAPTURED) {
            throw new SettlementException("only CAPTURED trades can be scheduled for settlement");
        }
    }

    private void validateSettlementPending(Trade trade) {
        if (trade.status() != TradeStatus.SETTLEMENT_PENDING) {
            throw new SettlementException("only SETTLEMENT_PENDING trades can be settled");
        }
    }

    private Trade transitionTrade(
            Trade trade,
            TradeStatus nextStatus,
            Instant settledAt,
            Instant updatedAt
    ) {
        return new Trade(
                trade.tradeId(),
                trade.orderId(),
                trade.intentId(),
                trade.portfolioId(),
                trade.instrumentId(),
                trade.side(),
                trade.quantity(),
                nextStatus,
                trade.capturedAt(),
                settledAt,
                updatedAt
        );
    }
}
