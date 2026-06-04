package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.execution.model.OrderStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderConversionController.class)
@ContextConfiguration(classes = {
        OrderConversionControllerTest.TestApplication.class,
        OrderConversionController.class,
        ExecutionExceptionHandler.class
})
class OrderConversionControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderConversionService orderConversionService;

    @Test
    void convertsRiskApprovedIntentToOrder() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000031001");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000032001");
        Order order = order(orderId, intentId);
        OrderIntent convertedIntent = orderIntent(intentId, OrderIntentStatus.CONVERTED_TO_ORDER);

        when(orderConversionService.convert(intentId))
                .thenReturn(new OrderConversionResult(order, convertedIntent));

        mockMvc.perform(post("/api/order-intents/{intentId}/orders", intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.order.intentId").value(intentId.toString()))
                .andExpect(jsonPath("$.order.status").value("CREATED"))
                .andExpect(jsonPath("$.intent.intentId").value(intentId.toString()))
                .andExpect(jsonPath("$.intent.status").value("CONVERTED_TO_ORDER"));
    }

    @Test
    void returnsNotFoundWhenIntentDoesNotExist() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000031002");

        when(orderConversionService.convert(intentId))
                .thenThrow(new OrderIntentNotFoundException("order intent not found"));

        mockMvc.perform(post("/api/order-intents/{intentId}/orders", intentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order intent not found"));
    }

    @Test
    void returnsConflictWhenIntentCannotBeConverted() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000031003");

        when(orderConversionService.convert(intentId))
                .thenThrow(new OrderConversionException(
                        "only RISK_APPROVED order intents can be converted to orders"
                ));

        mockMvc.perform(post("/api/order-intents/{intentId}/orders", intentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only RISK_APPROVED order intents can be converted to orders"));
    }

    private Order order(UUID orderId, UUID intentId) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new Order(
                orderId,
                intentId,
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                OrderStatus.CREATED,
                now,
                now
        );
    }

    private OrderIntent orderIntent(UUID intentId, OrderIntentStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
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
                status,
                "manual-key-1",
                "operator",
                now,
                now
        );
    }
}
