package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderFillExecution;
import com.multiassetoms.execution.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderExecutionReplayServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-29T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryOrderExecutionEventRepository executionEventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final OrderAuditTrailService auditTrailService = new OrderAuditTrailService(
            executionEventRepository,
            fillExecutionRepository
    );
    private final OrderExecutionReplayService service = new OrderExecutionReplayService(
            auditTrailService,
            fixedClock
    );

    @Test
    void replaysAcknowledgedOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015001");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015101",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-29T00:00:00Z"
        );

        OrderReplayResult result = service.replay(orderId, new BigDecimal("10"));

        assertEquals(OrderStatus.SENT, result.initialStatus());
        assertEquals(OrderStatus.ACKED, result.replayedStatus());
        assertEquals(BigDecimal.ZERO, result.replayedFilledQuantity());
        assertEquals(1, result.appliedEventCount());
        assertEquals(Instant.parse("2026-05-29T01:00:00Z"), result.replayedAt());
    }

    @Test
    void replaysPartiallyFilledOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015002");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015102",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-29T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000015202", "4", "2026-05-29T00:01:00Z");

        OrderReplayResult result = service.replay(orderId, new BigDecimal("10"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
        assertEquals(2, result.appliedEventCount());
    }

    @Test
    void replaysFullyFilledOrderFromMultipleFills() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015003");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015103",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-29T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000015203", "4", "2026-05-29T00:01:00Z");
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000015204", "6", "2026-05-29T00:02:00Z");

        OrderReplayResult result = service.replay(orderId, new BigDecimal("10"));

        assertEquals(OrderStatus.FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("10"), result.replayedFilledQuantity());
        assertEquals(3, result.appliedEventCount());
    }

    @Test
    void replaysRejectedOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015004");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015104",
                OrderExecutionEventType.REJECTED,
                "2026-05-29T00:00:00Z"
        );

        OrderReplayResult result = service.replay(orderId, new BigDecimal("10"));

        assertEquals(OrderStatus.REJECTED, result.replayedStatus());
        assertEquals(BigDecimal.ZERO, result.replayedFilledQuantity());
    }

    @Test
    void replaysCanceledOrderWithPartialFill() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015005");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015105",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-29T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000015205", "4", "2026-05-29T00:01:00Z");
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000015106",
                OrderExecutionEventType.CANCEL_CONFIRMED,
                "2026-05-29T00:03:00Z"
        );

        OrderReplayResult result = service.replay(orderId, new BigDecimal("10"));

        assertEquals(OrderStatus.CANCELED, result.replayedStatus());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
    }

    @Test
    void rejectsReplayWhenFilledQuantityExceedsOrderQuantity() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000015006");
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000015206", "11", "2026-05-29T00:01:00Z");

        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.replay(orderId, new BigDecimal("10"))
        );

        assertEquals("replayed filled quantity exceeds order quantity", exception.getMessage());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.replay(null, new BigDecimal("10"))
        );

        assertEquals("orderId is required", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveOrderQuantity() {
        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.replay(
                        UUID.fromString("00000000-0000-0000-0000-000000015099"),
                        BigDecimal.ZERO
                )
        );

        assertEquals("orderQuantity must be greater than zero", exception.getMessage());
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
