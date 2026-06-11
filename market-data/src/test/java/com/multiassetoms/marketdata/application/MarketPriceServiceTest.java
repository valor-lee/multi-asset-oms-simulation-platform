package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.infrastructure.InMemoryMarketPriceRepository;
import com.multiassetoms.marketdata.model.MarketDataException;
import com.multiassetoms.marketdata.model.MarketPrice;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketPriceServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-11T00:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryMarketPriceRepository marketPriceRepository =
            new InMemoryMarketPriceRepository();
    private final MarketPriceService service = new MarketPriceService(
            marketPriceRepository,
            fixedClock
    );

    @Test
    void upsertsLatestPrice() {
        MarketPrice price = service.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z")
        );

        assertEquals("005930", price.instrumentId());
        assertEquals(new BigDecimal("55000"), price.price());
        assertEquals(Instant.parse("2026-06-11T09:00:00Z"), price.observedAt());
        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), price.updatedAt());
        assertEquals(price, service.latestPrice("005930"));
    }

    @Test
    void usesUpdatedAtAsObservedAtWhenObservedAtIsMissing() {
        MarketPrice price = service.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                null
        );

        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), price.observedAt());
        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), price.updatedAt());
    }

    @Test
    void replacesLatestPriceForSameInstrument() {
        service.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z")
        );

        MarketPrice updated = service.upsertLatestPrice(
                "005930",
                new BigDecimal("56000"),
                Instant.parse("2026-06-11T10:00:00Z")
        );

        assertEquals(new BigDecimal("56000"), service.latestPrice("005930").price());
        assertEquals(updated, service.latestPrice("005930"));
    }

    @Test
    void rejectsBlankInstrumentId() {
        MarketDataException exception = assertThrows(
                MarketDataException.class,
                () -> service.upsertLatestPrice(" ", new BigDecimal("55000"), null)
        );

        assertEquals("instrumentId is required", exception.getMessage());
    }

    @Test
    void rejectsZeroOrNegativePrice() {
        MarketDataException exception = assertThrows(
                MarketDataException.class,
                () -> service.upsertLatestPrice("005930", BigDecimal.ZERO, null)
        );

        assertEquals("price must be greater than zero", exception.getMessage());
    }

    @Test
    void rejectsMissingPrice() {
        MarketDataException exception = assertThrows(
                MarketDataException.class,
                () -> service.upsertLatestPrice("005930", null, null)
        );

        assertEquals("price must be greater than zero", exception.getMessage());
    }

    @Test
    void rejectsMissingLatestPrice() {
        MarketPriceNotFoundException exception = assertThrows(
                MarketPriceNotFoundException.class,
                () -> service.latestPrice("005930")
        );

        assertEquals("market price not found", exception.getMessage());
    }
}
