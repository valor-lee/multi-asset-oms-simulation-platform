package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.application.port.RealizedPnlRepository;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import com.multiassetoms.posttrade.model.RealizedPnlException;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RealizedPnlService {

    private final TradeRepository tradeRepository;
    private final RealizedPnlRepository realizedPnlRepository;
    private final AverageCostService averageCostService;
    private final Clock clock;

    public RealizedPnlService(
            TradeRepository tradeRepository,
            RealizedPnlRepository realizedPnlRepository,
            AverageCostService averageCostService,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.realizedPnlRepository = realizedPnlRepository;
        this.averageCostService = averageCostService;
        this.clock = clock;
    }

    /**
     * settled SELL trade의 realized PnL을 기록한다.
     * 상태별 처리:
     * - SETTLED SELL: 매도대금에서 평균 원가, 수수료, 세금을 차감해 realized PnL 계산
     * - 이미 posting된 trade: 중복 요청으로 보고 기존 PnL entry 반환
     * - 그 외 상태 또는 BUY trade: realized PnL 기록 대상이 아니므로 예외
     *
     * @param tradeId realized PnL을 기록할 trade id
     * @param averageCost 매도 수량에 대응하는 평균 원가
     * @return 생성되었거나 이미 존재하는 realized PnL entry
     */
    public RealizedPnlEntry post(UUID tradeId, BigDecimal averageCost) {
        RealizedPnlEntry existingEntry = realizedPnlRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingEntry != null) {
            return existingEntry;
        }

        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("trade not found"));

        validatePostable(trade, averageCost);
        return realizedPnlRepository.save(toPnlEntry(trade, averageCost, Instant.now(clock)));
    }

    /**
     * settled SELL trade의 portfolio/instrument 현재 평균단가를 조회해 realized PnL을 기록한다.
     *
     * @param tradeId realized PnL을 기록할 trade id
     * @return 생성되었거나 이미 존재하는 realized PnL entry
     */
    public RealizedPnlEntry postWithCurrentAverageCost(UUID tradeId) {
        RealizedPnlEntry existingEntry = realizedPnlRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingEntry != null) {
            return existingEntry;
        }

        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("trade not found"));
        return post(tradeId, averageCostService.averageCostForRealizedPnl(trade));
    }

    /**
     * portfolio 기준 누적 realized PnL을 조회한다.
     *
     * @param portfolioId portfolio id
     * @return 현재 누적 realized PnL
     */
    public BigDecimal currentRealizedPnl(String portfolioId) {
        return realizedPnlRepository.currentRealizedPnl(portfolioId);
    }

    private void validatePostable(Trade trade, BigDecimal averageCost) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new RealizedPnlException("only SETTLED trades can be posted to realized PnL");
        }
        if (trade.side() != OrderSide.SELL) {
            throw new RealizedPnlException("only SELL trades can produce realized PnL");
        }
        if (trade.averageFillPrice() == null) {
            throw new RealizedPnlException("averageFillPrice is required to post realized PnL");
        }
        if (trade.grossNotional() == null) {
            throw new RealizedPnlException("grossNotional is required to post realized PnL");
        }
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new RealizedPnlException("averageCost must be zero or greater");
        }
    }

    private RealizedPnlEntry toPnlEntry(Trade trade, BigDecimal averageCost, Instant postedAt) {
        BigDecimal feeAmount = trade.feeAmount() == null ? BigDecimal.ZERO : trade.feeAmount();
        BigDecimal taxAmount = trade.taxAmount() == null ? BigDecimal.ZERO : trade.taxAmount();
        return new RealizedPnlEntry(
                UUID.randomUUID(),
                trade.tradeId(),
                trade.portfolioId(),
                trade.instrumentId(),
                trade.quantity(),
                trade.averageFillPrice(),
                averageCost,
                trade.grossNotional(),
                feeAmount,
                taxAmount,
                realizedPnl(trade, averageCost, feeAmount, taxAmount),
                postedAt
        );
    }

    private BigDecimal realizedPnl(
            Trade trade,
            BigDecimal averageCost,
            BigDecimal feeAmount,
            BigDecimal taxAmount
    ) {
        BigDecimal costBasis = trade.quantity().multiply(averageCost);
        return trade.grossNotional()
                .subtract(costBasis)
                .subtract(feeAmount)
                .subtract(taxAmount);
    }
}
