package com.multiassetoms.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderCancellationException;
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

@WebMvcTest(OrderCancellationController.class)
@ContextConfiguration(classes = {
        OrderCancellationControllerTest.TestApplication.class,
        OrderCancellationController.class,
        ExecutionExceptionHandler.class
})
class OrderCancellationControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderCancellationService orderCancellationService;

    @Test
    void requestsCancelForAckedOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000041001");
        Order cancelRequestedOrder = order(orderId, OrderStatus.CANCEL_REQUESTED, BigDecimal.ZERO);

        when(orderCancellationService.requestCancel(orderId)).thenReturn(cancelRequestedOrder);

        mockMvc.perform(post("/api/orders/{orderId}/cancel-requests", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));
    }

    @Test
    void confirmsCancelRequestedOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000041002");
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000042002");
        Order canceledOrder = order(orderId, OrderStatus.CANCELED, new BigDecimal("4"));

        when(orderCancellationService.confirmCancel(orderId, eventId)).thenReturn(canceledOrder);

        mockMvc.perform(post("/api/orders/{orderId}/cancel-confirmations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderExecutionEventRequest(eventId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.filledQuantity").value(4));
    }

    @Test
    void returnsBadRequestWhenCancelConfirmationEventIdIsMissing() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000041003");

        mockMvc.perform(post("/api/orders/{orderId}/cancel-confirmations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("eventId is required"));
    }

    @Test
    void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000041004");

        when(orderCancellationService.requestCancel(orderId))
                .thenThrow(new OrderNotFoundException("order not found"));

        mockMvc.perform(post("/api/orders/{orderId}/cancel-requests", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeCanceled() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000041005");

        when(orderCancellationService.requestCancel(orderId))
                .thenThrow(new OrderCancellationException(
                        "only ACKED or PARTIALLY_FILLED orders can be canceled"
                ));

        mockMvc.perform(post("/api/orders/{orderId}/cancel-requests", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only ACKED or PARTIALLY_FILLED orders can be canceled"));
    }

    private Order order(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000043001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                filledQuantity,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                Instant.parse("2026-06-05T00:00:00Z"),
                now
        );
    }
}
