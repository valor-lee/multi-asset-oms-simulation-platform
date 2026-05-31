package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderAuditEvent;
import com.multiassetoms.auditreplay.model.OrderAuditEventSource;
import com.multiassetoms.auditreplay.model.OrderAuditTrail;
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

@WebMvcTest(OrderAuditTrailController.class)
@ContextConfiguration(classes = {
        OrderAuditTrailControllerTest.TestApplication.class,
        OrderAuditTrailController.class,
        AuditReplayExceptionHandler.class
})
class OrderAuditTrailControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderAuditTrailService auditTrailService;

    @Test
    void returnsOrderAuditTrail() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000020001");
        when(auditTrailService.auditTrail(orderId)).thenReturn(new OrderAuditTrail(
                orderId,
                List.of(
                        new OrderAuditEvent(
                                UUID.fromString("00000000-0000-0000-0000-000000020101"),
                                orderId,
                                OrderAuditEventSource.ORDER_EXECUTION,
                                "ACKNOWLEDGED",
                                null,
                                null,
                                null,
                                null,
                                Instant.parse("2026-05-31T04:00:00Z")
                        ),
                        new OrderAuditEvent(
                                UUID.fromString("00000000-0000-0000-0000-000000020201"),
                                orderId,
                                OrderAuditEventSource.FILL_EXECUTION,
                                "FILL",
                                new BigDecimal("5"),
                                new BigDecimal("55000"),
                                new BigDecimal("15"),
                                new BigDecimal("5"),
                                Instant.parse("2026-05-31T04:01:00Z")
                        )
                )
        ));

        mockMvc.perform(get("/api/audit-replay/order-audit-trails/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("00000000-0000-0000-0000-000000020001"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].source").value("ORDER_EXECUTION"))
                .andExpect(jsonPath("$.events[0].eventType").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.events[0].occurredAt").value("2026-05-31T04:00:00Z"))
                .andExpect(jsonPath("$.events[1].source").value("FILL_EXECUTION"))
                .andExpect(jsonPath("$.events[1].eventType").value("FILL"))
                .andExpect(jsonPath("$.events[1].fillQuantity").value(5))
                .andExpect(jsonPath("$.events[1].fillPrice").value(55000))
                .andExpect(jsonPath("$.events[1].feeAmount").value(15))
                .andExpect(jsonPath("$.events[1].taxAmount").value(5));
    }

    @Test
    void returnsBadRequestWhenOrderIdIsInvalid() throws Exception {
        mockMvc.perform(get("/api/audit-replay/order-audit-trails/{orderId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid request argument"));
    }
}
