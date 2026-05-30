package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyReport;
import com.multiassetoms.auditreplay.model.OrderReplayMismatchReason;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderFillExecution;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderReplayConsistencyReportServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-31T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryOrderExecutionEventRepository executionEventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final OrderAuditTrailService auditTrailService = new OrderAuditTrailService(
            executionEventRepository,
            fillExecutionRepository
    );
    private final OrderExecutionReplayService replayService = new OrderExecutionReplayService(
            auditTrailService,
            fixedClock
    );
    private final OrderReplayConsistencyService consistencyService = new OrderReplayConsistencyService(
            orderRepository,
            replayService,
            fixedClock
    );
    private final OrderReplayConsistencyReportService reportService = new OrderReplayConsistencyReportService(
            orderRepository,
            consistencyService,
            fixedClock
    );

    @Test
    void returnsReportForAllOrders() {
        UUID consistentOrderId = UUID.fromString("00000000-0000-0000-0000-000000017001");
        UUID inconsistentOrderId = UUID.fromString("00000000-0000-0000-0000-000000017002");
        orderRepository.save(createOrder(
                consistentOrderId,
                "00000000-0000-0000-0000-000000017101",
                OrderStatus.FILLED,
                new BigDecimal("10"),
                "2026-05-31T00:00:00Z"
        ));
        orderRepository.save(createOrder(
                inconsistentOrderId,
                "00000000-0000-0000-0000-000000017102",
                OrderStatus.PARTIALLY_FILLED,
                new BigDecimal("3"),
                "2026-05-31T00:01:00Z"
        ));
        saveExecutionEvent(
                consistentOrderId,
                "00000000-0000-0000-0000-000000017201",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-31T00:00:10Z"
        );
        saveFillExecution(
                consistentOrderId,
                "00000000-0000-0000-0000-000000017301",
                "10",
                "2026-05-31T00:00:20Z"
        );
        saveExecutionEvent(
                inconsistentOrderId,
                "00000000-0000-0000-0000-000000017202",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-31T00:01:10Z"
        );
        saveFillExecution(
                inconsistentOrderId,
                "00000000-0000-0000-0000-000000017302",
                "4",
                "2026-05-31T00:01:20Z"
        );

        OrderReplayConsistencyReport report = reportService.checkAll();

        assertEquals(2, report.totalCount());
        assertEquals(1, report.consistentCount());
        assertEquals(1, report.inconsistentCount());
        assertEquals(Instant.parse("2026-05-31T01:00:00Z"), report.checkedAt());
        assertEquals(consistentOrderId, report.results().get(0).orderId());
        assertTrue(report.results().get(0).consistent());
        assertEquals(inconsistentOrderId, report.results().get(1).orderId());
        assertEquals(
                List.of(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH),
                report.results().get(1).mismatchReasons()
        );
    }

    @Test
    void returnsEmptyReportWhenThereAreNoOrders() {
        OrderReplayConsistencyReport report = reportService.checkAll();

        assertEquals(0, report.totalCount());
        assertEquals(0, report.consistentCount());
        assertEquals(0, report.inconsistentCount());
        assertTrue(report.results().isEmpty());
        assertEquals(Instant.parse("2026-05-31T01:00:00Z"), report.checkedAt());
    }

    private Order createOrder(
            UUID orderId,
            String intentId,
            OrderStatus status,
            BigDecimal filledQuantity,
            String createdAt
    ) {
        Instant createdAtInstant = Instant.parse(createdAt);
        return new Order(
                orderId,
                UUID.fromString(intentId),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                filledQuantity,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                createdAtInstant,
                createdAtInstant
        );
    }

    private void saveExecutionEvent(
            UUID orderId,
            String eventId,
            OrderExecutionEventType eventType,
            String processedAt
    ) {
        executionEventRepository.save(new OrderExecutionEvent(
                UUID.fromString(eventId),
                orderId,
                eventType,
                Instant.parse(processedAt)
        ));
    }

    private void saveFillExecution(
            UUID orderId,
            String fillExecutionId,
            String fillQuantity,
            String processedAt
    ) {
        fillExecutionRepository.save(new OrderFillExecution(
                UUID.fromString(fillExecutionId),
                orderId,
                new BigDecimal(fillQuantity),
                new BigDecimal("55000"),
                null,
                null,
                Instant.parse(processedAt)
        ));
    }
}
