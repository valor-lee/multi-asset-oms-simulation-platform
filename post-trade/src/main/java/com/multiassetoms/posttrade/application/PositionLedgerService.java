package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.application.port.PositionLedgerRepository;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.PositionKey;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
import com.multiassetoms.posttrade.model.PositionLedgerException;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PositionLedgerService {

    private final TradeRepository tradeRepository;
    private final PositionLedgerRepository positionLedgerRepository;
    private final Clock clock;

    public PositionLedgerService(
            TradeRepository tradeRepository,
            PositionLedgerRepository positionLedgerRepository,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.positionLedgerRepository = positionLedgerRepository;
        this.clock = clock;
    }

    /**
     * settled trade를 position ledger에 반영한다.
     * 상태별 처리:
     * - SETTLED: BUY는 양수, SELL은 음수 position delta로 posting
     * - 이미 posting된 trade: 중복 요청으로 보고 기존 ledger entry 반환
     * - 그 외 상태: position 반영 대상이 아니므로 예외
     *
     * @param tradeId position ledger에 반영할 trade id
     * @return 생성되었거나 이미 존재하는 position ledger entry
     */
    public PositionLedgerEntry post(UUID tradeId) {
        PositionLedgerEntry existingEntry = positionLedgerRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingEntry != null) {
            return existingEntry;
        }

        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new PositionLedgerException("trade not found"));

        validatePostable(trade);
        return positionLedgerRepository.save(toLedgerEntry(trade, Instant.now(clock)));
    }

    /**
     * portfolio/instrument 기준 현재 position quantity를 조회한다.
     *
     * @param portfolioId portfolio id
     * @param instrumentId instrument id
     * @return 현재 position quantity
     */
    public BigDecimal currentPosition(String portfolioId, String instrumentId) {
        return positionLedgerRepository.currentPosition(new PositionKey(portfolioId, instrumentId));
    }

    private void validatePostable(Trade trade) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new PositionLedgerException("only SETTLED trades can be posted to position ledger");
        }
    }

    private PositionLedgerEntry toLedgerEntry(Trade trade, Instant postedAt) {
        return new PositionLedgerEntry(
                UUID.randomUUID(),
                trade.tradeId(),
                trade.portfolioId(),
                trade.instrumentId(),
                trade.side(),
                quantityDelta(trade),
                postedAt
        );
    }

    private BigDecimal quantityDelta(Trade trade) {
        if (trade.side() == OrderSide.BUY) {
            return trade.quantity();
        }
        return trade.quantity().negate();
    }
}
