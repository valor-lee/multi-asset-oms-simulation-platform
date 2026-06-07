package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import com.multiassetoms.posttrade.model.LedgerPostingException;
import com.multiassetoms.posttrade.model.LedgerPostingResult;
import com.multiassetoms.posttrade.model.PositionLedgerEntry;
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

@WebMvcTest(PostSettlementLedgerController.class)
@ContextConfiguration(classes = {
        PostSettlementLedgerControllerTest.TestApplication.class,
        PostSettlementLedgerController.class,
        PostTradeExceptionHandler.class
})
class PostSettlementLedgerControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostSettlementLedgerService postSettlementLedgerService;

    @Test
    void postsSettledTradeToLedgers() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000049001");
        LedgerPostingResult result = result(tradeId);

        when(postSettlementLedgerService.post(tradeId)).thenReturn(result);

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/ledger-postings", tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionLedgerEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.positionLedgerEntry.quantityDelta").value(10))
                .andExpect(jsonPath("$.cashLedgerEntry.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.cashLedgerEntry.cashDelta").value(-550100));
    }

    @Test
    void returnsNotFoundWhenTradeDoesNotExist() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000049002");

        when(postSettlementLedgerService.post(tradeId))
                .thenThrow(new TradeNotFoundException("trade not found"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/ledger-postings", tradeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("trade not found"));
    }

    @Test
    void returnsConflictWhenTradeCannotBePosted() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000049003");

        when(postSettlementLedgerService.post(tradeId))
                .thenThrow(new LedgerPostingException("only SETTLED trades can be posted to ledgers"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/ledger-postings", tradeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only SETTLED trades can be posted to ledgers"));
    }

    private LedgerPostingResult result(UUID tradeId) {
        Instant postedAt = Instant.parse("2026-06-07T00:00:00Z");
        return new LedgerPostingResult(
                new PositionLedgerEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000050001"),
                        tradeId,
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        new BigDecimal("10"),
                        postedAt
                ),
                new CashLedgerEntry(
                        UUID.fromString("00000000-0000-0000-0000-000000051001"),
                        tradeId,
                        "portfolio-1",
                        OrderSide.BUY,
                        new BigDecimal("-550100"),
                        postedAt
                )
        );
    }
}
