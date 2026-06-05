package com.multiassetoms.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderAcknowledgementException;
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

@WebMvcTest(OrderAcknowledgementController.class)
@ContextConfiguration(classes = {
        OrderAcknowledgementControllerTest.TestApplication.class,
        OrderAcknowledgementController.class,
        ExecutionExceptionHandler.class
})
class OrderAcknowledgementControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderAcknowledgementService orderAcknowledgementService;

    @Test
    void acknowledgesSentOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000035001");
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000036001");
        Order acknowledgedOrder = order(orderId, OrderStatus.ACKED);

        when(orderAcknowledgementService.acknowledge(orderId, eventId)).thenReturn(acknowledgedOrder);

        mockMvc.perform(post("/api/orders/{orderId}/acknowledgements", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderExecutionEventRequest(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("ACKED"));
    }

    @Test
    void rejectsSentOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000035002");
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000036002");
        Order rejectedOrder = order(orderId, OrderStatus.REJECTED);

        when(orderAcknowledgementService.reject(orderId, eventId)).thenReturn(rejectedOrder);

        mockMvc.perform(post("/api/orders/{orderId}/rejections", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderExecutionEventRequest(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void returnsBadRequestWhenEventIdIsMissing() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000035003");

        mockMvc.perform(post("/api/orders/{orderId}/acknowledgements", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("eventId is required"));
    }

    @Test
    void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000035004");
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000036004");

        when(orderAcknowledgementService.acknowledge(orderId, eventId))
                .thenThrow(new OrderNotFoundException("order not found"));

        mockMvc.perform(post("/api/orders/{orderId}/acknowledgements", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderExecutionEventRequest(eventId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeAcknowledged() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000035005");
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000036005");

        when(orderAcknowledgementService.acknowledge(orderId, eventId))
                .thenThrow(new OrderAcknowledgementException(
                        "only SENT orders can be acknowledged or rejected"
                ));

        mockMvc.perform(post("/api/orders/{orderId}/acknowledgements", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderExecutionEventRequest(eventId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only SENT orders can be acknowledged or rejected"));
    }

    private Order order(UUID orderId, OrderStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000037001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                Instant.parse("2026-06-04T00:00:00Z"),
                now
        );
    }
}
