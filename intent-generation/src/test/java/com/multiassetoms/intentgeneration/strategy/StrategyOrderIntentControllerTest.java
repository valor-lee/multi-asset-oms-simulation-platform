package com.multiassetoms.intentgeneration.strategy;

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

@WebMvcTest(StrategyOrderIntentController.class)
@ContextConfiguration(classes = {
        StrategyOrderIntentControllerTest.TestApplication.class,
        StrategyOrderIntentController.class,
        OrderIntentExceptionHandler.class
})
class StrategyOrderIntentControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StrategyOrderIntentService strategyOrderIntentService;

    @Test
    void createsStrategyOrderIntent() throws Exception {
        StrategyOrderIntentRequest request = new StrategyOrderIntentRequest(
                "portfolio-1",
                "005930",
                "signal-1",
                OrderSide.SELL,
                OrderType.MARKET,
                new BigDecimal("5"),
                null,
                TimeInForce.DAY,
                "momentum signal",
                "strategy-key-1",
                "strategy-engine"
        );
        OrderIntent response = response(
                "00000000-0000-0000-0000-000000025001",
                "signal-1",
                "strategy-key-1"
        );

        when(strategyOrderIntentService.create(any(StrategyOrderIntentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/order-intents/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentId").value("00000000-0000-0000-0000-000000025001"))
                .andExpect(jsonPath("$.sourceType").value("STRATEGY"))
                .andExpect(jsonPath("$.sourceRefId").value("signal-1"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("strategy-key-1"));
    }

    @Test
    void returnsBadRequestWhenValidationFails() throws Exception {
        StrategyOrderIntentRequest request = new StrategyOrderIntentRequest(
                "portfolio-1",
                "005930",
                "signal-1",
                OrderSide.SELL,
                OrderType.MARKET,
                BigDecimal.ZERO,
                null,
                TimeInForce.DAY,
                "momentum signal",
                "strategy-key-1",
                "strategy-engine"
        );
        when(strategyOrderIntentService.create(any(StrategyOrderIntentRequest.class)))
                .thenThrow(new OrderIntentValidationException("requestedQty must be greater than zero"));

        mockMvc.perform(post("/api/order-intents/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requestedQty must be greater than zero"));
    }

    private OrderIntent response(
            String intentId,
            String strategySignalId,
            String idempotencyKey
    ) {
        return new OrderIntent(
                UUID.fromString(intentId),
                "portfolio-1",
                "005930",
                OrderIntentSourceType.STRATEGY,
                strategySignalId,
                OrderSide.SELL,
                OrderType.MARKET,
                new BigDecimal("5"),
                null,
                TimeInForce.DAY,
                "momentum signal",
                OrderIntentStatus.CREATED,
                idempotencyKey,
                "strategy-engine",
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        );
    }
}
