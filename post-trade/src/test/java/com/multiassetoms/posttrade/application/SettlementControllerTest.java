package com.multiassetoms.posttrade.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.posttrade.model.Settlement;
import com.multiassetoms.posttrade.model.SettlementException;
import com.multiassetoms.posttrade.model.SettlementNotFoundException;
import com.multiassetoms.posttrade.model.SettlementStatus;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
@ContextConfiguration(classes = {
        SettlementControllerTest.TestApplication.class,
        SettlementController.class,
        PostTradeExceptionHandler.class
})
class SettlementControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    @Test
    void schedulesCapturedTradeForSettlement() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000047001");
        UUID settlementId = UUID.fromString("00000000-0000-0000-0000-000000048001");
        SettlementScheduleRequest request = new SettlementScheduleRequest(LocalDate.parse("2026-06-09"));
        Settlement settlement = settlement(settlementId, tradeId, SettlementStatus.PENDING, null);

        when(settlementService.scheduleSettlement(tradeId, request.settlementDate()))
                .thenReturn(settlement);

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/settlements", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(settlementId.toString()))
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.settlementDate").value("2026-06-09"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void confirmsPendingSettlement() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000047002");
        UUID settlementId = UUID.fromString("00000000-0000-0000-0000-000000048002");
        Instant settledAt = Instant.parse("2026-06-10T00:00:00Z");
        Settlement settlement = settlement(settlementId, tradeId, SettlementStatus.SETTLED, settledAt);

        when(settlementService.confirmSettlement(settlementId)).thenReturn(settlement);

        mockMvc.perform(post("/api/post-trade/settlements/{settlementId}/confirmations", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(settlementId.toString()))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settledAt").value("2026-06-10T00:00:00Z"));
    }

    @Test
    void returnsBadRequestWhenSettlementDateIsMissing() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000047003");

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/settlements", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("settlementDate is required"));
    }

    @Test
    void returnsNotFoundWhenTradeDoesNotExist() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000047004");
        SettlementScheduleRequest request = new SettlementScheduleRequest(LocalDate.parse("2026-06-09"));

        when(settlementService.scheduleSettlement(tradeId, request.settlementDate()))
                .thenThrow(new TradeNotFoundException("trade not found"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/settlements", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("trade not found"));
    }

    @Test
    void returnsNotFoundWhenSettlementDoesNotExist() throws Exception {
        UUID settlementId = UUID.fromString("00000000-0000-0000-0000-000000048005");

        when(settlementService.confirmSettlement(settlementId))
                .thenThrow(new SettlementNotFoundException("settlement not found"));

        mockMvc.perform(post("/api/post-trade/settlements/{settlementId}/confirmations", settlementId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("settlement not found"));
    }

    @Test
    void returnsConflictWhenTradeCannotBeScheduled() throws Exception {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000047006");
        SettlementScheduleRequest request = new SettlementScheduleRequest(LocalDate.parse("2026-06-09"));

        when(settlementService.scheduleSettlement(tradeId, request.settlementDate()))
                .thenThrow(new SettlementException("only CAPTURED trades can be scheduled for settlement"));

        mockMvc.perform(post("/api/post-trade/trades/{tradeId}/settlements", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only CAPTURED trades can be scheduled for settlement"));
    }

    private Settlement settlement(
            UUID settlementId,
            UUID tradeId,
            SettlementStatus status,
            Instant settledAt
    ) {
        Instant createdAt = Instant.parse("2026-06-07T00:00:00Z");
        return new Settlement(
                settlementId,
                tradeId,
                LocalDate.parse("2026-06-09"),
                status,
                createdAt,
                settledAt,
                settledAt == null ? createdAt : settledAt
        );
    }
}
