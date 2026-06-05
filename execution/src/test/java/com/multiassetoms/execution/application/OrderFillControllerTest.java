package com.multiassetoms.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillException;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderStatus;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderFillController.class)
@ContextConfiguration(classes = {
        OrderFillControllerTest.TestApplication.class,
        OrderFillController.class,
        ExecutionExceptionHandler.class
})
class OrderFillControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderFillService orderFillService;

    @Test
    void partiallyFillsAckedOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038001");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000039001");
        OrderFillRequest request = new OrderFillRequest(
                fillExecutionId,
                new BigDecimal("4"),
                new BigDecimal("55000"),
                new BigDecimal("40"),
                new BigDecimal("12")
        );

        when(orderFillService.fill(
                orderId,
                fillExecutionId,
                new BigDecimal("4"),
                new BigDecimal("55000"),
                new BigDecimal("40"),
                new BigDecimal("12")
        )).thenReturn(order(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("4")));

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.filledQuantity").value(4));
    }

    @Test
    void fullyFillsOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038002");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000039002");
        OrderFillRequest request = new OrderFillRequest(
                fillExecutionId,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                null,
                null
        );

        when(orderFillService.fill(
                orderId,
                fillExecutionId,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                null,
                null
        )).thenReturn(order(orderId, OrderStatus.FILLED, new BigDecimal("10")));

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.filledQuantity").value(10));
    }

    @Test
    void returnsBadRequestWhenFillExecutionIdIsMissing() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038003");

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fillQuantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fillExecutionId is required"));
    }

    @Test
    void returnsBadRequestWhenFillQuantityIsInvalid() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038004");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000039004");

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fillExecutionId": "%s",
                                  "fillQuantity": 0
                                }
                                """.formatted(fillExecutionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fillQuantity must be greater than zero"));
    }

    @Test
    void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038005");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000039005");
        OrderFillRequest request = new OrderFillRequest(fillExecutionId, new BigDecimal("1"), null, null, null);

        when(orderFillService.fill(orderId, fillExecutionId, new BigDecimal("1"), null, null, null))
                .thenThrow(new OrderNotFoundException("order not found"));

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeFilled() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000038006");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000039006");
        OrderFillRequest request = new OrderFillRequest(fillExecutionId, new BigDecimal("1"), null, null, null);

        when(orderFillService.fill(orderId, fillExecutionId, new BigDecimal("1"), null, null, null))
                .thenThrow(new OrderFillException(
                        "only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled"
                ));

        mockMvc.perform(post("/api/orders/{orderId}/fills", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled"));
    }

    private Order order(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000040001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                filledQuantity,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                Instant.parse("2026-06-04T00:00:00Z"),
                now
        );
    }
}
