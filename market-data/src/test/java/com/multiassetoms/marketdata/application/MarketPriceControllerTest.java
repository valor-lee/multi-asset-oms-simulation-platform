package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.model.MarketDataException;
import com.multiassetoms.marketdata.model.MarketPrice;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketPriceController.class)
@ContextConfiguration(classes = {
        MarketPriceControllerTest.TestApplication.class,
        MarketPriceController.class,
        MarketDataExceptionHandler.class
})
class MarketPriceControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketPriceService marketPriceService;

    @Test
    void upsertsLatestPrice() throws Exception {
        MarketPrice price = price();

        when(marketPriceService.upsertLatestPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z")
        )).thenReturn(price);

        mockMvc.perform(put("/api/market-data/instruments/{instrumentId}/prices/latest", "005930")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 55000,
                                  "observedAt": "2026-06-11T09:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.price").value(55000))
                .andExpect(jsonPath("$.observedAt").value("2026-06-11T09:00:00Z"));
    }

    @Test
    void rejectsMissingPrice() throws Exception {
        mockMvc.perform(put("/api/market-data/instruments/{instrumentId}/prices/latest", "005930")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("price is required"));
    }

    @Test
    void returnsBadRequestWhenPriceIsInvalid() throws Exception {
        when(marketPriceService.upsertLatestPrice("005930", BigDecimal.ZERO, null))
                .thenThrow(new MarketDataException("price must be greater than zero"));

        mockMvc.perform(put("/api/market-data/instruments/{instrumentId}/prices/latest", "005930")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("price must be greater than zero"));
    }

    @Test
    void getsLatestPrice() throws Exception {
        when(marketPriceService.latestPrice("005930")).thenReturn(price());

        mockMvc.perform(get("/api/market-data/instruments/{instrumentId}/prices/latest", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.price").value(55000));
    }

    @Test
    void returnsNotFoundWhenLatestPriceDoesNotExist() throws Exception {
        when(marketPriceService.latestPrice("005930"))
                .thenThrow(new MarketPriceNotFoundException("market price not found"));

        mockMvc.perform(get("/api/market-data/instruments/{instrumentId}/prices/latest", "005930"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("market price not found"));
    }

    private MarketPrice price() {
        return new MarketPrice(
                "005930",
                new BigDecimal("55000"),
                Instant.parse("2026-06-11T09:00:00Z"),
                Instant.parse("2026-06-11T09:00:01Z")
        );
    }
}
