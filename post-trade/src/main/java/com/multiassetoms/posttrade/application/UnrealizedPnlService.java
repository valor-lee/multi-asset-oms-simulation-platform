package com.multiassetoms.posttrade.application;

import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.model.MarketPrice;
import com.multiassetoms.posttrade.model.CurrentAverageCost;
import com.multiassetoms.posttrade.model.UnrealizedPnlException;
import com.multiassetoms.posttrade.model.UnrealizedPnlSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class UnrealizedPnlService {

    private final PositionLedgerService positionLedgerService;
    private final MarketPriceService marketPriceService;
    private final AverageCostService averageCostService;
    private final Clock clock;

    public UnrealizedPnlService(
            PositionLedgerService positionLedgerService,
            MarketPriceService marketPriceService,
            AverageCostService averageCostService,
            Clock clock
    ) {
        this.positionLedgerService = positionLedgerService;
        this.marketPriceService = marketPriceService;
        this.averageCostService = averageCostService;
        this.clock = clock;
    }

    /**
     * portfolio/instrument의 현재 position을 기준으로 평가손익 snapshot을 계산한다.
     *
     * @param portfolioId portfolio id
     * @param instrumentId instrument id
     * @param averageCost 현재 position의 평균 원가
     * @param marketPrice 현재 시장 가격
     * @return 평가금액과 평가손익 snapshot
     */
    public UnrealizedPnlSnapshot snapshot(
            String portfolioId,
            String instrumentId,
            BigDecimal averageCost,
            BigDecimal marketPrice
    ) {
        validateInputs(portfolioId, instrumentId, averageCost, marketPrice);

        BigDecimal quantity = positionLedgerService.currentPosition(portfolioId, instrumentId);
        validateQuantity(quantity);

        BigDecimal costBasis = quantity.multiply(averageCost);
        BigDecimal marketValue = quantity.multiply(marketPrice);
        return new UnrealizedPnlSnapshot(
                portfolioId,
                instrumentId,
                quantity,
                averageCost,
                marketPrice,
                costBasis,
                marketValue,
                marketValue.subtract(costBasis),
                Instant.now(clock)
        );
    }

    /**
     * market-data에 저장된 최신 시장 가격을 사용해 평가손익 snapshot을 계산한다.
     *
     * @param portfolioId portfolio id
     * @param instrumentId instrument id
     * @param averageCost 현재 position의 평균 원가
     * @return 최신 시장 가격 기준 평가금액과 평가손익 snapshot
     */
    public UnrealizedPnlSnapshot snapshotWithLatestMarketPrice(
            String portfolioId,
            String instrumentId,
            BigDecimal averageCost
    ) {
        validateInputs(portfolioId, instrumentId, averageCost, BigDecimal.ZERO);
        MarketPrice latestPrice = marketPriceService.latestPrice(instrumentId);
        return snapshot(portfolioId, instrumentId, averageCost, latestPrice.price());
    }

    /**
     * average cost와 market-data latest price를 모두 내부 조회해 평가손익 snapshot을 계산한다.
     */
    public UnrealizedPnlSnapshot snapshotWithCurrentAverageCostAndLatestMarketPrice(
            String portfolioId,
            String instrumentId
    ) {
        validatePortfolioAndInstrument(portfolioId, instrumentId);
        CurrentAverageCost currentAverageCost = averageCostService.currentAverageCost(portfolioId, instrumentId);
        MarketPrice latestPrice = marketPriceService.latestPrice(instrumentId);
        return snapshot(portfolioId, instrumentId, currentAverageCost.averageCost(), latestPrice.price());
    }

    private void validateInputs(
            String portfolioId,
            String instrumentId,
            BigDecimal averageCost,
            BigDecimal marketPrice
    ) {
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new UnrealizedPnlException("portfolioId is required");
        }
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new UnrealizedPnlException("instrumentId is required");
        }
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnrealizedPnlException("averageCost must be zero or greater");
        }
        if (marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnrealizedPnlException("marketPrice must be zero or greater");
        }
    }

    private void validatePortfolioAndInstrument(String portfolioId, String instrumentId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new UnrealizedPnlException("portfolioId is required");
        }
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new UnrealizedPnlException("instrumentId is required");
        }
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnrealizedPnlException("short positions are not supported for unrealized PnL");
        }
    }
}
