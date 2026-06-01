package com.multiassetoms.intentgeneration.rebalancing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.intentgeneration.api.OrderIntentExceptionHandler;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderIntentValidationException;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RebalancingOrderIntentController.class)
@ContextConfiguration(classes = {
        RebalancingOrderIntentControllerTest.TestApplication.class,
        RebalancingOrderIntentController.class,
        OrderIntentExceptionHandler.class
})
class RebalancingOrderIntentControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RebalancingOrderIntentService rebalancingOrderIntentService;

    @Test
    void createsRebalancingOrderIntent() throws Exception {
        RebalancingOrderIntentRequest request = new RebalancingOrderIntentRequest(
                "portfolio-1",
                "005930",
                "rebalance-run-1",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "rebalance drift",
                "rebalance-key-1",
                "rebalancer"
        );
        OrderIntent response = response(
                "00000000-0000-0000-0000-000000024001",
                OrderIntentSourceType.REBALANCING,
                "rebalance-run-1",
                "rebalance-key-1",
                "rebalancer"
        );

        when(rebalancingOrderIntentService.create(any(RebalancingOrderIntentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/order-intents/rebalancing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentId").value("00000000-0000-0000-0000-000000024001"))
                .andExpect(jsonPath("$.sourceType").value("REBALANCING"))
                .andExpect(jsonPath("$.sourceRefId").value("rebalance-run-1"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("rebalance-key-1"));
    }

    @Test
    void returnsBadRequestWhenValidationFails() throws Exception {
        RebalancingOrderIntentRequest request = new RebalancingOrderIntentRequest(
                "portfolio-1",
                "005930",
                "rebalance-run-1",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                null,
                TimeInForce.DAY,
                "rebalance drift",
                "rebalance-key-1",
                "rebalancer"
        );
        when(rebalancingOrderIntentService.create(any(RebalancingOrderIntentRequest.class)))
                .thenThrow(new OrderIntentValidationException("limitPrice is required for LIMIT orders"));

        mockMvc.perform(post("/api/order-intents/rebalancing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limitPrice is required for LIMIT orders"));
    }

    private OrderIntent response(
            String intentId,
            OrderIntentSourceType sourceType,
            String sourceReferenceId,
            String idempotencyKey,
            String createdBy
    ) {
        return new OrderIntent(
                UUID.fromString(intentId),
                "portfolio-1",
                "005930",
                sourceType,
                sourceReferenceId,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "rebalance drift",
                OrderIntentStatus.CREATED,
                idempotencyKey,
                createdBy,
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        );
    }
}
