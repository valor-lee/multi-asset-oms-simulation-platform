package com.multiassetoms.marketdata.infrastructure;

import com.multiassetoms.marketdata.application.port.MarketPriceRepository;
import com.multiassetoms.marketdata.model.MarketPrice;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryMarketPriceRepository implements MarketPriceRepository {

    private final Map<String, MarketPrice> latestPricesByInstrumentId = new ConcurrentHashMap<>();

    @Override
    public MarketPrice save(MarketPrice marketPrice) {
        latestPricesByInstrumentId.put(marketPrice.instrumentId(), marketPrice);
        return marketPrice;
    }

    @Override
    public Optional<MarketPrice> findLatestByInstrumentId(String instrumentId) {
        return Optional.ofNullable(latestPricesByInstrumentId.get(instrumentId));
    }
}
