package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostException;
import com.multiassetoms.posttrade.model.AverageCostSnapshot;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AverageCostController.class)
@ContextConfiguration(classes = {
        AverageCostControllerTest.TestApplication.class,
        AverageCostController.class,
        PostTradeExceptionHandler.class
})
class AverageCostControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AverageCostService averageCostService;

    @Test
    void postsTradeToAverageCost() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000078001");

        when(averageCostService.post(tradeId)).thenReturn(entry(tradeId));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/average-cost-postings", tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.costBasis").value(550100))
                .andExpect(jsonPath("$.averageCost").value(55010));
    }

    @Test
    void getsCurrentAverageCost() throws Exception {
        when(averageCostService.currentAverageCost("portfolio-1", "005930"))
                .thenReturn(snapshot());

        mockMvc.perform(get(
                        "/api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/average-cost",
                        "portfolio-1",
                        "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.costBasis").value(550100))
                .andExpect(jsonPath("$.averageCost").value(55010));
    }

    @Test
    void returnsNotFoundWhenTradeDoesNotExist() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000078002");

        when(averageCostService.post(tradeId))
                .thenThrow(new TradeNotFoundException("trade not found"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/average-cost-postings", tradeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("trade not found"));
    }

    @Test
    void returnsConflictWhenTradeCannotBePosted() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000078003");

        when(averageCostService.post(tradeId))
                .thenThrow(new AverageCostException("only SETTLED trades can be posted to average cost"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/average-cost-postings", tradeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only SETTLED trades can be posted to average cost"));
    }

    private AverageCostEntry entry(UUID tradeId) {
        return new AverageCostEntry(
                UUID.fromString("00000000-0000-0000-0000-000000079001"),
                tradeId,
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("550100"),
                new BigDecimal("10"),
                new BigDecimal("550100"),
                new BigDecimal("55010"),
                Instant.parse("2026-06-21T00:00:00Z")
        );
    }

    private AverageCostSnapshot snapshot() {
        return new AverageCostSnapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("550100"),
                new BigDecimal("55010"),
                Instant.parse("2026-06-21T00:00:00Z")
        );
    }
}
