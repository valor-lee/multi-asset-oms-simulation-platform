package com.multiassetoms.intentgeneration.api;

import com.multiassetoms.intentgeneration.application.OrderIntentQueryService;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderIntentQueryController.class)
@ContextConfiguration(classes = {
        OrderIntentQueryControllerTest.TestApplication.class,
        OrderIntentQueryController.class,
        OrderIntentExceptionHandler.class
})
class OrderIntentQueryControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderIntentQueryService orderIntentQueryService;

    @Test
    void getsOrderIntentByIntentId() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000027001");
        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(orderIntent(intentId, "query-key-1"));

        mockMvc.perform(get("/api/order-intents/{intentId}", intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentId").value("00000000-0000-0000-0000-000000027001"))
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("query-key-1"));
    }

    @Test
    void getsOrderIntentByIdempotencyKey() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000027002");
        when(orderIntentQueryService.getByIdempotencyKey("query-key-2"))
                .thenReturn(orderIntent(intentId, "query-key-2"));

        mockMvc.perform(get("/api/order-intents")
                        .queryParam("idempotencyKey", "query-key-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentId").value("00000000-0000-0000-0000-000000027002"))
                .andExpect(jsonPath("$.idempotencyKey").value("query-key-2"));
    }

    @Test
    void returnsNotFoundWhenOrderIntentDoesNotExist() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000027003");
        when(orderIntentQueryService.getByIntentId(intentId))
                .thenThrow(new OrderIntentNotFoundException("order intent not found"));

        mockMvc.perform(get("/api/order-intents/{intentId}", intentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order intent not found"));
    }

    private OrderIntent orderIntent(UUID intentId, String idempotencyKey) {
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        return new OrderIntent(
                intentId,
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                null,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "manual order",
                OrderIntentStatus.CREATED,
                idempotencyKey,
                "operator",
                now,
                now
        );
    }
}
