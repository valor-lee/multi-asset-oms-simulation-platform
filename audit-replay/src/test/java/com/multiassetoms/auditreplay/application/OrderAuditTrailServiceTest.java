package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderAuditEventSource;
import com.multiassetoms.auditreplay.model.OrderAuditTrail;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderFillExecution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAuditTrailServiceTest {

    private final InMemoryOrderExecutionEventRepository executionEventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final OrderAuditTrailService service = new OrderAuditTrailService(
            executionEventRepository,
            fillExecutionRepository
    );

    @Test
    void returnsOrderAuditTrailSortedByOccurredAt() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001001");
        saveFillExecution(
                orderId,
                "00000000-0000-0000-0000-000000003001",
                "4",
                "55000",
                "40",
                "12",
                "2026-05-21T01:01:00Z"
        );
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000004001",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-21T01:00:00Z"
        );
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000004002",
                OrderExecutionEventType.CANCEL_CONFIRMED,
                "2026-05-21T01:03:00Z"
        );

        OrderAuditTrail auditTrail = service.auditTrail(orderId);

        assertEquals(orderId, auditTrail.orderId());
        assertEquals(3, auditTrail.events().size());
        assertEquals(OrderAuditEventSource.ORDER_EXECUTION, auditTrail.events().get(0).source());
        assertEquals("ACKNOWLEDGED", auditTrail.events().get(0).eventType());
        assertEquals(OrderAuditEventSource.FILL_EXECUTION, auditTrail.events().get(1).source());
        assertEquals("FILL", auditTrail.events().get(1).eventType());
        assertEquals(OrderAuditEventSource.ORDER_EXECUTION, auditTrail.events().get(2).source());
        assertEquals("CANCEL_CONFIRMED", auditTrail.events().get(2).eventType());
    }

    @Test
    void keepsFillExecutionDetailsInAuditTrail() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001002");
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000003002");
        saveFillExecution(
                orderId,
                fillExecutionId.toString(),
                "6",
                "55500",
                "60",
                "18",
                "2026-05-21T01:02:00Z"
        );

        OrderAuditTrail auditTrail = service.auditTrail(orderId);

        assertEquals(1, auditTrail.events().size());
        assertEquals(fillExecutionId, auditTrail.events().get(0).eventId());
        assertEquals(OrderAuditEventSource.FILL_EXECUTION, auditTrail.events().get(0).source());
        assertEquals("FILL", auditTrail.events().get(0).eventType());
        assertEquals(new BigDecimal("6"), auditTrail.events().get(0).fillQuantity());
        assertEquals(new BigDecimal("55500"), auditTrail.events().get(0).fillPrice());
        assertEquals(new BigDecimal("60"), auditTrail.events().get(0).feeAmount());
        assertEquals(new BigDecimal("18"), auditTrail.events().get(0).taxAmount());
    }

    @Test
    void returnsEmptyTrailWhenOrderHasNoEvents() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001099");

        OrderAuditTrail auditTrail = service.auditTrail(orderId);

        assertEquals(orderId, auditTrail.orderId());
        assertTrue(auditTrail.events().isEmpty());
    }

    @Test
    void rejectsMissingOrderId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.auditTrail(null)
        );

        assertEquals("orderId is required", exception.getMessage());
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
            String fillPrice,
            String feeAmount,
            String taxAmount,
            String processedAt
    ) {
        fillExecutionRepository.save(new OrderFillExecution(
                UUID.fromString(fillExecutionId),
                orderId,
                new BigDecimal(fillQuantity),
                new BigDecimal(fillPrice),
                new BigDecimal(feeAmount),
                new BigDecimal(taxAmount),
                Instant.parse(processedAt)
        ));
    }
}
