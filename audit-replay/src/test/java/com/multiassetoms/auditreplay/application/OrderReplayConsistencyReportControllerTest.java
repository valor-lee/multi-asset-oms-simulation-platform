package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyReport;
import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
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

@WebMvcTest(OrderReplayConsistencyReportController.class)
@ContextConfiguration(classes = {
        OrderReplayConsistencyReportControllerTest.TestApplication.class,
        OrderReplayConsistencyReportController.class
})
class OrderReplayConsistencyReportControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderReplayConsistencyReportService reportService;

    @Test
    void returnsAllConsistencyReportByDefault() throws Exception {
        when(reportService.checkAll()).thenReturn(new OrderReplayConsistencyReport(
                2,
                1,
                1,
                List.of(
                        result(
                                "00000000-0000-0000-0000-000000018001",
                                true,
                                List.of(),
                                OrderStatus.FILLED,
                                OrderStatus.FILLED,
                                "10",
                                "10"
                        ),
                        result(
                                "00000000-0000-0000-0000-000000018002",
                                false,
                                List.of(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH),
                                OrderStatus.PARTIALLY_FILLED,
                                OrderStatus.PARTIALLY_FILLED,
                                "3",
                                "4"
                        )
                ),
                Instant.parse("2026-05-31T02:00:00Z")
        ));

        mockMvc.perform(get("/api/audit-replay/order-replay/consistency-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.consistentCount").value(1))
                .andExpect(jsonPath("$.inconsistentCount").value(1))
                .andExpect(jsonPath("$.results[0].orderId").value("00000000-0000-0000-0000-000000018001"))
                .andExpect(jsonPath("$.results[0].consistent").value(true))
                .andExpect(jsonPath("$.results[1].mismatchReasons[0]").value("FILLED_QUANTITY_MISMATCH"))
                .andExpect(jsonPath("$.checkedAt").value("2026-05-31T02:00:00Z"));
    }

    @Test
    void returnsOnlyInconsistentReportWhenRequested() throws Exception {
        when(reportService.checkInconsistentOnly()).thenReturn(new OrderReplayConsistencyReport(
                2,
                1,
                1,
                List.of(result(
                        "00000000-0000-0000-0000-000000018003",
                        false,
                        List.of(OrderReplayMismatchReason.STATUS_MISMATCH),
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.FILLED,
                        "10",
                        "10"
                )),
                Instant.parse("2026-05-31T02:05:00Z")
        ));

        mockMvc.perform(get("/api/audit-replay/order-replay/consistency-report")
                        .param("inconsistentOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.consistentCount").value(1))
                .andExpect(jsonPath("$.inconsistentCount").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].orderId").value("00000000-0000-0000-0000-000000018003"))
                .andExpect(jsonPath("$.results[0].mismatchReasons[0]").value("STATUS_MISMATCH"))
                .andExpect(jsonPath("$.checkedAt").value("2026-05-31T02:05:00Z"));
    }

    private OrderReplayConsistencyResult result(
            String orderId,
            boolean consistent,
            List<OrderReplayMismatchReason> mismatchReasons,
            OrderStatus actualStatus,
            OrderStatus replayedStatus,
            String actualFilledQuantity,
            String replayedFilledQuantity
    ) {
        return new OrderReplayConsistencyResult(
                UUID.fromString(orderId),
                consistent,
                mismatchReasons,
                actualStatus,
                replayedStatus,
                new BigDecimal(actualFilledQuantity),
                new BigDecimal(replayedFilledQuantity),
                2,
                Instant.parse("2026-05-31T02:00:00Z")
        );
    }
}
