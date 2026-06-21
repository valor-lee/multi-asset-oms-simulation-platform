package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.application.port.AverageCostRepository;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostException;
import com.multiassetoms.posttrade.model.CurrentAverageCost;
import com.multiassetoms.posttrade.model.PositionKey;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AverageCostService {

    private static final int COST_SCALE = 10;

    private final TradeRepository tradeRepository;
    private final AverageCostRepository averageCostRepository;
    private final Clock clock;

    public AverageCostService(
            TradeRepository tradeRepository,
            AverageCostRepository averageCostRepository,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.averageCostRepository = averageCostRepository;
        this.clock = clock;
    }

    /**
     * settled trade를 현재 position의 평균단가 계산 원장에 반영한다.
     *
     * @param tradeId 평균단가 원장에 반영할 trade id
     * @return 생성되었거나 이미 존재하는 평균단가 entry
     */
    public AverageCostEntry post(UUID tradeId) {
        AverageCostEntry existingEntry = averageCostRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingEntry != null) {
            return existingEntry;
        }

        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("trade not found"));

        validatePostable(trade);
        CurrentAverageCost currentSnapshot = currentAverageCost(trade.portfolioId(), trade.instrumentId());
        return averageCostRepository.save(toEntry(trade, currentSnapshot, Instant.now(clock)));
    }

    /**
     * portfolio/instrument 기준 현재 position quantity, cost basis, average cost를 조회한다.
     */
    public CurrentAverageCost currentAverageCost(String portfolioId, String instrumentId) {
        return averageCostRepository.currentAverageCost(new PositionKey(portfolioId, instrumentId));
    }

    private void validatePostable(Trade trade) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new AverageCostException("only SETTLED trades can be posted to average cost");
        }
        if (trade.quantity() == null || trade.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AverageCostException("trade quantity must be greater than zero");
        }
        if (trade.grossNotional() == null) {
            throw new AverageCostException("grossNotional is required to post average cost");
        }
    }

    private AverageCostEntry toEntry(
            Trade trade,
            CurrentAverageCost currentSnapshot,
            Instant postedAt
    ) {
        BigDecimal currentQuantity = currentSnapshot.quantity();
        BigDecimal currentCostBasis = currentSnapshot.costBasis();
        BigDecimal costDelta = costDelta(trade, currentSnapshot);
        BigDecimal nextQuantity = nextQuantity(trade, currentQuantity);
        BigDecimal nextCostBasis = normalizeCostBasis(nextQuantity, currentCostBasis.add(costDelta));
        BigDecimal nextAverageCost = averageCost(nextQuantity, nextCostBasis);

        return new AverageCostEntry(
                UUID.randomUUID(),
                trade.tradeId(),
                trade.portfolioId(),
                trade.instrumentId(),
                trade.side(),
                trade.quantity(),
                costDelta,
                nextQuantity,
                nextCostBasis,
                nextAverageCost,
                postedAt
        );
    }

    private BigDecimal costDelta(Trade trade, CurrentAverageCost currentSnapshot) {
        if (trade.side() == OrderSide.BUY) {
            BigDecimal feeAmount = trade.feeAmount() == null ? BigDecimal.ZERO : trade.feeAmount();
            BigDecimal taxAmount = trade.taxAmount() == null ? BigDecimal.ZERO : trade.taxAmount();
            return trade.grossNotional().add(feeAmount).add(taxAmount);
        }
        validateSellQuantity(trade, currentSnapshot);
        return trade.quantity().multiply(currentSnapshot.averageCost()).negate();
    }

    private void validateSellQuantity(Trade trade, CurrentAverageCost currentSnapshot) {
        if (currentSnapshot.quantity().compareTo(trade.quantity()) < 0) {
            throw new AverageCostException("sell quantity exceeds current position");
        }
    }

    private BigDecimal nextQuantity(Trade trade, BigDecimal currentQuantity) {
        if (trade.side() == OrderSide.BUY) {
            return currentQuantity.add(trade.quantity());
        }
        return currentQuantity.subtract(trade.quantity());
    }

    private BigDecimal normalizeCostBasis(BigDecimal quantity, BigDecimal costBasis) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return costBasis;
    }

    private BigDecimal averageCost(BigDecimal quantity, BigDecimal costBasis) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return costBasis.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
    }
}
