package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.application.port.MarketPriceRepository;
import com.multiassetoms.marketdata.model.MarketDataException;
import com.multiassetoms.marketdata.model.MarketPrice;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class MarketPriceService {

    private final MarketPriceRepository marketPriceRepository;
    private final Clock clock;

    public MarketPriceService(MarketPriceRepository marketPriceRepository, Clock clock) {
        this.marketPriceRepository = marketPriceRepository;
        this.clock = clock;
    }

    /**
     * instrument별 최신 시장 가격을 저장한다.
     * 같은 instrument에 가격이 다시 들어오면 최신 가격 리소스를 교체한다.
     *
     * @param instrumentId 가격을 저장할 instrument id
     * @param price 시장 가격
     * @param observedAt 가격 관측 시각. null이면 저장 시각을 사용한다.
     * @return 저장된 최신 시장 가격
     */
    public MarketPrice upsertLatestPrice(
            String instrumentId,
            BigDecimal price,
            Instant observedAt
    ) {
        validateInstrumentId(instrumentId);
        validatePrice(price);

        Instant now = Instant.now(clock);
        Instant priceObservedAt = observedAt == null ? now : observedAt;
        return marketPriceRepository.save(new MarketPrice(
                instrumentId,
                price,
                priceObservedAt,
                now
        ));
    }

    /**
     * instrument별 최신 시장 가격을 조회한다.
     *
     * @param instrumentId 조회할 instrument id
     * @return instrument의 최신 시장 가격
     */
    public MarketPrice latestPrice(String instrumentId) {
        validateInstrumentId(instrumentId);
        return marketPriceRepository.findLatestByInstrumentId(instrumentId)
                .orElseThrow(() -> new MarketPriceNotFoundException("market price not found"));
    }

    private void validateInstrumentId(String instrumentId) {
        if (instrumentId == null || instrumentId.isBlank()) {
            throw new MarketDataException("instrumentId is required");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MarketDataException("price must be greater than zero");
        }
    }
}
