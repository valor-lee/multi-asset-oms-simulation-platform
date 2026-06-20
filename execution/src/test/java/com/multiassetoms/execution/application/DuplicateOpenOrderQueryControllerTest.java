package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionRequestException;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
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

@WebMvcTest(DuplicateOpenOrderQueryController.class)
@ContextConfiguration(classes = {
        DuplicateOpenOrderQueryControllerTest.TestApplication.class,
        DuplicateOpenOrderQueryController.class,
        ExecutionExceptionHandler.class
})
class DuplicateOpenOrderQueryControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DuplicateOpenOrderQueryService duplicateOpenOrderQueryService;

    @Test
    void findsDuplicateOpenOrder() throws Exception {
        Order order = order();

        when(duplicateOpenOrderQueryService.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                null
        )).thenReturn(DuplicateOpenOrderResult.found(order));

        mockMvc.perform(get("/api/orders/duplicate-open-order")
                        .queryParam("portfolioId", "portfolio-1")
                        .queryParam("instrumentId", "005930")
                        .queryParam("side", "BUY")
                        .queryParam("orderType", "LIMIT")
                        .queryParam("quantity", "10")
                        .queryParam("limitPrice", "55000")
                        .queryParam("timeInForce", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOpenOrderExists").value(true))
                .andExpect(jsonPath("$.duplicateOpenOrderId").value(order.orderId().toString()))
                .andExpect(jsonPath("$.duplicateOpenOrder.orderId").value(order.orderId().toString()));
    }

    @Test
    void returnsNoDuplicateOpenOrder() throws Exception {
        when(duplicateOpenOrderQueryService.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                null
        )).thenReturn(DuplicateOpenOrderResult.notFound());

        mockMvc.perform(get("/api/orders/duplicate-open-order")
                        .queryParam("portfolioId", "portfolio-1")
                        .queryParam("instrumentId", "005930")
                        .queryParam("side", "BUY")
                        .queryParam("orderType", "LIMIT")
                        .queryParam("quantity", "10")
                        .queryParam("limitPrice", "55000")
                        .queryParam("timeInForce", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOpenOrderExists").value(false))
                .andExpect(jsonPath("$.duplicateOpenOrderId").doesNotExist())
                .andExpect(jsonPath("$.duplicateOpenOrder").doesNotExist());
    }

    @Test
    void returnsBadRequestWhenQueryIsInvalid() throws Exception {
        when(duplicateOpenOrderQueryService.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                null,
                TimeInForce.DAY,
                null
        )).thenThrow(new ExecutionRequestException("limitPrice is required for LIMIT orders"));

        mockMvc.perform(get("/api/orders/duplicate-open-order")
                        .queryParam("portfolioId", "portfolio-1")
                        .queryParam("instrumentId", "005930")
                        .queryParam("side", "BUY")
                        .queryParam("orderType", "LIMIT")
                        .queryParam("quantity", "10")
                        .queryParam("timeInForce", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limitPrice is required for LIMIT orders"));
    }

    private Order order() {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new Order(
                UUID.fromString("00000000-0000-0000-0000-000000062001"),
                UUID.fromString("00000000-0000-0000-0000-000000063001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                OrderStatus.ACKED,
                now,
                now
        );
    }
}
