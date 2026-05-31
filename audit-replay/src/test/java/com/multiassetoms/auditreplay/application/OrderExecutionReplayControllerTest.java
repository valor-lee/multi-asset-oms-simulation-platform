package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayResult;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderExecutionReplayController.class)
@ContextConfiguration(classes = {
        OrderExecutionReplayControllerTest.TestApplication.class,
        OrderExecutionReplayController.class
})
class OrderExecutionReplayControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderExecutionReplayService replayService;

    @Test
    void returnsReplayResultForOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000019001");
        when(replayService.replay(orderId, new BigDecimal("10"))).thenReturn(new OrderReplayResult(
                orderId,
                OrderStatus.SENT,
                OrderStatus.FILLED,
                new BigDecimal("10"),
                new BigDecimal("10"),
                3,
                Instant.parse("2026-05-31T03:00:00Z")
        ));

        mockMvc.perform(get("/api/audit-replay/order-replay/{orderId}", orderId)
                        .param("orderQuantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("00000000-0000-0000-0000-000000019001"))
                .andExpect(jsonPath("$.initialStatus").value("SENT"))
                .andExpect(jsonPath("$.replayedStatus").value("FILLED"))
                .andExpect(jsonPath("$.orderQuantity").value(10))
                .andExpect(jsonPath("$.replayedFilledQuantity").value(10))
                .andExpect(jsonPath("$.appliedEventCount").value(3))
                .andExpect(jsonPath("$.replayedAt").value("2026-05-31T03:00:00Z"));
    }
}
