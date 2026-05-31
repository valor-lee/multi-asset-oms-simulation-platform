package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayMismatchReason;
import com.multiassetoms.execution.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderReplayConsistencyController.class)
@ContextConfiguration(classes = {
        OrderReplayConsistencyControllerTest.TestApplication.class,
        OrderReplayConsistencyController.class,
        AuditReplayExceptionHandler.class
})
class OrderReplayConsistencyControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderReplayConsistencyService consistencyService;

    @Test
    void returnsOrderReplayConsistencyResult() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000021001");
        when(consistencyService.check(orderId)).thenReturn(new OrderReplayConsistencyResult(
                orderId,
                false,
                List.of(OrderReplayMismatchReason.STATUS_MISMATCH),
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.FILLED,
                new BigDecimal("10"),
                new BigDecimal("10"),
                3,
                Instant.parse("2026-05-31T04:10:00Z")
        ));

        mockMvc.perform(get("/api/audit-replay/order-replay/consistency/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("00000000-0000-0000-0000-000000021001"))
                .andExpect(jsonPath("$.consistent").value(false))
                .andExpect(jsonPath("$.mismatchReasons[0]").value("STATUS_MISMATCH"))
                .andExpect(jsonPath("$.actualStatus").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.replayedStatus").value("FILLED"))
                .andExpect(jsonPath("$.actualFilledQuantity").value(10))
                .andExpect(jsonPath("$.replayedFilledQuantity").value(10))
                .andExpect(jsonPath("$.appliedEventCount").value(3))
                .andExpect(jsonPath("$.checkedAt").value("2026-05-31T04:10:00Z"));
    }

    @Test
    void returnsBadRequestWhenConsistencyCheckFails() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000021099");
        when(consistencyService.check(orderId)).thenThrow(new OrderReplayException("order not found"));

        mockMvc.perform(get("/api/audit-replay/order-replay/consistency/{orderId}", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("order not found"));
    }
}
