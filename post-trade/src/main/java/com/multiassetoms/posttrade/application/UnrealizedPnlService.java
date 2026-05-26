package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.UnrealizedPnlException;
import com.multiassetoms.posttrade.model.UnrealizedPnlSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class UnrealizedPnlService {

    private final PositionLedgerService positionLedgerService;
    private final Clock clock;

    public UnrealizedPnlService(PositionLedgerService positionLedgerService, Clock clock) {
        this.positionLedgerService = positionLedgerService;
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

    private void validateQuantity(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new UnrealizedPnlException("short positions are not supported for unrealized PnL");
        }
    }
}
