package com.multiassetoms.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.execution.model.ExecutionSimulationException;
import com.multiassetoms.execution.model.ExecutionSimulationResult;
import com.multiassetoms.execution.model.ExecutionSimulationStatus;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
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

@WebMvcTest(ExecutionSimulationController.class)
@ContextConfiguration(classes = {
        ExecutionSimulationControllerTest.TestApplication.class,
        ExecutionSimulationController.class,
        ExecutionExceptionHandler.class
})
class ExecutionSimulationControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExecutionSimulationService executionSimulationService;

    @Test
    void simulatesMarketExecution() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072001");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073001");
        ExecutionSimulationRequest request = new ExecutionSimulationRequest(
                simulationId,
                new BigDecimal("4"),
                new BigDecimal("0.01")
        );
        when(executionSimulationService.simulate(
                orderId,
                simulationId,
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        )).thenReturn(new ExecutionSimulationResult(
                simulationId,
                orderId,
                ExecutionSimulationStatus.FILLED,
                new BigDecimal("55000"),
                new BigDecimal("55550"),
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                80L,
                order(orderId)
        ));

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.simulationStatus").value("FILLED"))
                .andExpect(jsonPath("$.referencePrice").value(55000))
                .andExpect(jsonPath("$.fillPrice").value(55550))
                .andExpect(jsonPath("$.rejectRate").value(0))
                .andExpect(jsonPath("$.delayMillis").value(80))
                .andExpect(jsonPath("$.order.status").value("PARTIALLY_FILLED"));
    }

    @Test
    void returnsBadRequestWhenSimulationIdIsMissing() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072002");

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fillQuantity": 4,
                                  "slippageRate": 0.01
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("simulationId is required"));
    }

    @Test
    void returnsBadRequestWhenSlippageRateIsInvalid() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072003");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073003");

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "simulationId": "%s",
                                  "fillQuantity": 4,
                                  "slippageRate": 1
                                }
                                """.formatted(simulationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("slippageRate must be zero or greater and less than one"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeSimulated() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072004");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073004");
        ExecutionSimulationRequest request = new ExecutionSimulationRequest(
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO
        );
        when(executionSimulationService.simulate(
                orderId,
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        )).thenThrow(new ExecutionSimulationException(
                "only SENT, ACKED, or PARTIALLY_FILLED orders can be simulated"
        ));

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only SENT, ACKED, or PARTIALLY_FILLED orders can be simulated"));
    }

    @Test
    void returnsNotFoundWhenLatestMarketPriceDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072005");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073005");
        ExecutionSimulationRequest request = new ExecutionSimulationRequest(
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO
        );
        when(executionSimulationService.simulate(
                orderId,
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        )).thenThrow(new MarketPriceNotFoundException("market price not found"));

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("market price not found"));
    }

    @Test
    void returnsBadRequestWhenRejectRateIsInvalid() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072006");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073006");

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "simulationId": "%s",
                                  "fillQuantity": 4,
                                  "slippageRate": 0,
                                  "rejectRate": 1.1
                                }
                                """.formatted(simulationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("rejectRate must be zero or greater and less than or equal to one"));
    }

    @Test
    void returnsRejectedSimulationResult() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000072007");
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000073007");
        ExecutionSimulationRequest request = new ExecutionSimulationRequest(
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO,
                new BigDecimal("1.0")
        );
        when(executionSimulationService.simulate(
                orderId,
                simulationId,
                new BigDecimal("4"),
                BigDecimal.ZERO,
                new BigDecimal("1.0")
        )).thenReturn(new ExecutionSimulationResult(
                simulationId,
                orderId,
                ExecutionSimulationStatus.REJECTED,
                null,
                null,
                new BigDecimal("4"),
                BigDecimal.ZERO,
                new BigDecimal("1.0"),
                120L,
                rejectedOrder(orderId)
        ));

        mockMvc.perform(post("/api/orders/{orderId}/execution-simulations", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.referencePrice").doesNotExist())
                .andExpect(jsonPath("$.fillPrice").doesNotExist())
                .andExpect(jsonPath("$.rejectRate").value(1.0))
                .andExpect(jsonPath("$.order.status").value("REJECTED"));
    }

    private Order order(UUID orderId) {
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000074001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("10"),
                new BigDecimal("4"),
                null,
                TimeInForce.DAY,
                OrderStatus.PARTIALLY_FILLED,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z")
        );
    }

    private Order rejectedOrder(UUID orderId) {
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000074002"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                null,
                TimeInForce.DAY,
                OrderStatus.REJECTED,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z")
        );
    }
}
