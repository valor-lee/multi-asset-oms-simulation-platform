package com.multiassetoms.posttrade.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerBalanceController.class)
@ContextConfiguration(classes = {
        LedgerBalanceControllerTest.TestApplication.class,
        LedgerBalanceController.class,
        PostTradeExceptionHandler.class
})
class LedgerBalanceControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionLedgerService positionLedgerService;

    @MockBean
    private CashLedgerService cashLedgerService;

    @Test
    void getsCurrentPosition() throws Exception {
        when(positionLedgerService.currentPosition("portfolio-1", "005930"))
                .thenReturn(new BigDecimal("10"));

        mockMvc.perform(get("/api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}",
                        "portfolio-1",
                        "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void getsCurrentCash() throws Exception {
        when(cashLedgerService.currentCash("portfolio-1"))
                .thenReturn(new BigDecimal("-550100"));

        mockMvc.perform(get("/api/post-trade/portfolios/{portfolioId}/cash", "portfolio-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.cash").value(-550100));
    }
}
