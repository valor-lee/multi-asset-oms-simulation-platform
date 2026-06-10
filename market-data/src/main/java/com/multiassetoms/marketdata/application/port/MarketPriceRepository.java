package com.multiassetoms.marketdata.application.port;

import com.multiassetoms.marketdata.model.MarketPrice;

import java.util.Optional;

public interface MarketPriceRepository {

    MarketPrice save(MarketPrice marketPrice);

    Optional<MarketPrice> findLatestByInstrumentId(String instrumentId);
}
