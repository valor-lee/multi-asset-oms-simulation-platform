package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.execution.model.OrderSubmissionException;
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

@WebMvcTest(OrderSubmissionController.class)
@ContextConfiguration(classes = {
        OrderSubmissionControllerTest.TestApplication.class,
        OrderSubmissionController.class,
        ExecutionExceptionHandler.class
})
class OrderSubmissionControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderSubmissionService orderSubmissionService;

    @Test
    void submitsCreatedOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000033001");
        Order submittedOrder = order(orderId, OrderStatus.SENT);

        when(orderSubmissionService.submit(orderId)).thenReturn(submittedOrder);

        mockMvc.perform(post("/api/orders/{orderId}/submissions", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-05T00:00:00Z"));
    }

    @Test
    void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000033002");

        when(orderSubmissionService.submit(orderId))
                .thenThrow(new OrderNotFoundException("order not found"));

        mockMvc.perform(post("/api/orders/{orderId}/submissions", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeSubmitted() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000033003");

        when(orderSubmissionService.submit(orderId))
                .thenThrow(new OrderSubmissionException("only CREATED orders can be submitted"));

        mockMvc.perform(post("/api/orders/{orderId}/submissions", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("only CREATED orders can be submitted"));
    }

    private Order order(UUID orderId, OrderStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000034001"),
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
