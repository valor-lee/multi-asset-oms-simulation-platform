package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import com.multiassetoms.posttrade.model.RealizedPnlException;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.UnrealizedPnlSnapshot;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PnlController.class)
@ContextConfiguration(classes = {
        PnlControllerTest.TestApplication.class,
        PnlController.class,
        PostTradeExceptionHandler.class
})
class PnlControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RealizedPnlService realizedPnlService;

    @MockBean
    private UnrealizedPnlService unrealizedPnlService;

    @Test
    void postsRealizedPnl() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000052001");
        RealizedPnlEntry entry = realizedPnlEntry(tradeId);

        when(realizedPnlService.post(tradeId, new BigDecimal("54000"))).thenReturn(entry);

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/realized-pnl-postings", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "averageCost": 54000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.realizedPnl").value(9870));
    }

    @Test
    void postsRealizedPnlWithCurrentAverageCost() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000052005");
        RealizedPnlEntry entry = realizedPnlEntry(tradeId);

        when(realizedPnlService.postWithCurrentAverageCost(tradeId)).thenReturn(entry);

        mockMvc.perform(post(
                        "/api/post-trade/trades/{tradeId}/realized-pnl-postings/current-average-cost",
                        tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.realizedPnl").value(9870));
    }

    @Test
    void rejectsMissingAverageCost() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000052002");

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/realized-pnl-postings", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("averageCost is required"));
    }

    @Test
    void returnsNotFoundWhenTradeDoesNotExist() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000052003");

        when(realizedPnlService.post(tradeId, new BigDecimal("54000")))
                .thenThrow(new TradeNotFoundException("trade not found"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/realized-pnl-postings", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "averageCost": 54000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("trade not found"));
    }

    @Test
    void returnsConflictWhenRealizedPnlCannotBePosted() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000052004");

        when(realizedPnlService.post(tradeId, new BigDecimal("54000")))
                .thenThrow(new RealizedPnlException("only SELL trades can produce realized PnL"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/realized-pnl-postings", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "averageCost": 54000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only SELL trades can produce realized PnL"));
    }

    @Test
    void getsCurrentRealizedPnl() throws Exception {
        when(realizedPnlService.currentRealizedPnl("portfolio-1"))
                .thenReturn(new BigDecimal("9870"));

        mockMvc.perform(get("/api/post-trade/portfolios/{portfolioId}/realized-pnl", "portfolio-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.realizedPnl").value(9870));
    }

    @Test
    void getsUnrealizedPnlSnapshot() throws Exception {
        UnrealizedPnlSnapshot snapshot = new UnrealizedPnlSnapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("54000"),
                new BigDecimal("55000"),
                new BigDecimal("540000"),
                new BigDecimal("550000"),
                new BigDecimal("10000"),
                Instant.parse("2026-06-07T00:00:00Z")
        );

        when(unrealizedPnlService.snapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("54000"),
                new BigDecimal("55000")
        )).thenReturn(snapshot);

        mockMvc.perform(get("/api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl",
                        "portfolio-1",
                        "005930")
                        .queryParam("averageCost", "54000")
                        .queryParam("marketPrice", "55000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.unrealizedPnl").value(10000));
    }

    @Test
    void getsUnrealizedPnlSnapshotWithLatestMarketPrice() throws Exception {
        UnrealizedPnlSnapshot snapshot = new UnrealizedPnlSnapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("54000"),
                new BigDecimal("55000"),
                new BigDecimal("540000"),
                new BigDecimal("550000"),
                new BigDecimal("10000"),
                Instant.parse("2026-06-07T00:00:00Z")
        );

        when(unrealizedPnlService.snapshotWithLatestMarketPrice(
                "portfolio-1",
                "005930",
                new BigDecimal("54000")
        )).thenReturn(snapshot);

        mockMvc.perform(get("/api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest",
                        "portfolio-1",
                        "005930")
                        .queryParam("averageCost", "54000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.marketPrice").value(55000))
                .andExpect(jsonPath("$.unrealizedPnl").value(10000));
    }

    @Test
    void getsUnrealizedPnlSnapshotWithCurrentAverageCostAndLatestMarketPrice() throws Exception {
        UnrealizedPnlSnapshot snapshot = new UnrealizedPnlSnapshot(
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("54000"),
                new BigDecimal("55000"),
                new BigDecimal("540000"),
                new BigDecimal("550000"),
                new BigDecimal("10000"),
                Instant.parse("2026-06-07T00:00:00Z")
        );

        when(unrealizedPnlService.snapshotWithCurrentAverageCostAndLatestMarketPrice(
                "portfolio-1",
                "005930"
        )).thenReturn(snapshot);

        mockMvc.perform(get(
                        "/api/post-trade/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest/current-average-cost",
                        "portfolio-1",
                        "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("portfolio-1"))
                .andExpect(jsonPath("$.instrumentId").value("005930"))
                .andExpect(jsonPath("$.averageCost").value(54000))
                .andExpect(jsonPath("$.marketPrice").value(55000))
                .andExpect(jsonPath("$.unrealizedPnl").value(10000));
    }

    private RealizedPnlEntry realizedPnlEntry(UUID tradeId) {
        return new RealizedPnlEntry(
                UUID.fromString("00000000-0000-0000-0000-000000053001"),
                tradeId,
                "portfolio-1",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("55000.0000000000"),
                new BigDecimal("54000"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                new BigDecimal("9870"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
    }
}
