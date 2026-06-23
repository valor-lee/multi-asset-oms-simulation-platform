package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.AccountingPostingResult;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import com.multiassetoms.posttrade.model.LedgerPostingException;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
import com.multiassetoms.posttrade.model.RealizedPnlEntry;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostSettlementAccountingController.class)
@ContextConfiguration(classes = {
        PostSettlementAccountingControllerTest.TestApplication.class,
        PostSettlementAccountingController.class,
        PostTradeExceptionHandler.class
})
class PostSettlementAccountingControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostSettlementAccountingService postSettlementAccountingService;

    @Test
    void postsSettledTradeToAccounting() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000083001");

        when(postSettlementAccountingService.post(tradeId)).thenReturn(result(tradeId));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/accounting-postings", tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionLedgerEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.cashLedgerEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.averageCostEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.realizedPnlEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.realizedPnlEntry.realizedPnl").value(3920));
    }

    @Test
    void returnsNotFoundWhenTradeDoesNotExist() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000083002");

        when(postSettlementAccountingService.post(tradeId))
                .thenThrow(new TradeNotFoundException("trade not found"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/accounting-postings", tradeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("trade not found"));
    }

    @Test
    void returnsConflictWhenTradeCannotBePosted() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000083003");

        when(postSettlementAccountingService.post(tradeId))
                .thenThrow(new LedgerPostingException("only SETTLED trades can be posted to accounting"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/accounting-postings", tradeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only SETTLED trades can be posted to accounting"));
    }

    private AccountingPostingResult result(UUID tradeId) {
        Instant postedAt = Instant.parse("2026-06-23T00:00:00Z");
        return new AccountingPostingResult(
                new PositionLedgerEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000084001"),
                        tradeId,
                        "portfolio-1",
                        "005930",
                        OrderSide.SELL,
                        new BigDecimal("-4"),
                        postedAt
                ),
                new CashLedgerEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000085001"),
                        tradeId,
                        "portfolio-1",
                        OrderSide.SELL,
                        new BigDecimal("219920"),
                        postedAt
                ),
                new AverageCostEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000086001"),
                        tradeId,
                        "portfolio-1",
                        "005930",
                        OrderSide.SELL,
                        new BigDecimal("4"),
                        new BigDecimal("-216000.0000000000"),
                        new BigDecimal("6"),
                        new BigDecimal("324000.0000000000"),
                        new BigDecimal("54000.0000000000"),
                        postedAt
                ),
                new RealizedPnlEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000087001"),
                        tradeId,
                        "portfolio-1",
                        "005930",
                        new BigDecimal("4"),
                        new BigDecimal("55000"),
                        new BigDecimal("54000.0000000000"),
                        new BigDecimal("220000"),
                        new BigDecimal("80"),
                        BigDecimal.ZERO,
                        new BigDecimal("3920.0000000000"),
                        postedAt
                )
        );
    }
}
