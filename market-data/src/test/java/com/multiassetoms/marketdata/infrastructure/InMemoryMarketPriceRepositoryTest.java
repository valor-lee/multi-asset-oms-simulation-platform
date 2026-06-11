package com.multiassetoms.marketdata.infrastructure;

import com.multiassetoms.marketdata.model.MarketPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMarketPriceRepositoryTest {

    private final InMemoryMarketPriceRepository repository = new InMemoryMarketPriceRepository();

    @Test
    void savesAndFindsLatestPriceByInstrumentId() {
        MarketPrice price = new MarketPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z"),
                Instant.parse("2026-06-11T09:00:01Z")
        );

        repository.save(price);

        Optional<MarketPrice> found = repository.findLatestByInstrumentId("005930");

        assertTrue(found.isPresent());
        assertEquals(price, found.get());
    }

    @Test
    void replacesExistingLatestPrice() {
        repository.save(new MarketPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z"),
                Instant.parse("2026-06-11T09:00:01Z")
        ));
        MarketPrice updated = new MarketPrice(
                "005930",
                new BigDecimal("56000"),
                Instant.parse("2026-06-11T10:00:00Z"),
                Instant.parse("2026-06-11T10:00:01Z")
        );

        repository.save(updated);

        assertEquals(updated, repository.findLatestByInstrumentId("005930").get());
    }
}
