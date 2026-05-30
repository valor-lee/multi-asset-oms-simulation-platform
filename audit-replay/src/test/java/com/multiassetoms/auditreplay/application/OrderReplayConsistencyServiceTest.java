package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderReplayConsistencyServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-30T01:00:00Z"),
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
    private final OrderReplayConsistencyService service = new OrderReplayConsistencyService(
            orderRepository,
            replayService,
            fixedClock
    );

    @Test
    void returnsConsistentWhenOrderMatchesReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016001");
        orderRepository.save(createOrder(orderId, OrderStatus.FILLED, new BigDecimal("10")));
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000016101",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-30T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016201", "4", "2026-05-30T00:01:00Z");
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016202", "6", "2026-05-30T00:02:00Z");

        OrderReplayConsistencyResult result = service.check(orderId);

        assertTrue(result.consistent());
        assertTrue(result.mismatchReasons().isEmpty());
        assertEquals(OrderStatus.FILLED, result.actualStatus());
        assertEquals(OrderStatus.FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("10"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("10"), result.replayedFilledQuantity());
        assertEquals(3, result.appliedEventCount());
        assertEquals(Instant.parse("2026-05-30T01:00:00Z"), result.checkedAt());
    }

    @Test
    void returnsInconsistentWhenOrderStatusDiffersFromReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016002");
        orderRepository.save(createOrder(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("10")));
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000016102",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-30T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016203", "4", "2026-05-30T00:01:00Z");
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016204", "6", "2026-05-30T00:02:00Z");

        OrderReplayConsistencyResult result = service.check(orderId);

        assertFalse(result.consistent());
        assertEquals(
                List.of(OrderReplayMismatchReason.STATUS_MISMATCH),
                result.mismatchReasons()
        );
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.actualStatus());
        assertEquals(OrderStatus.FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("10"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("10"), result.replayedFilledQuantity());
    }

    @Test
    void returnsInconsistentWhenFilledQuantityDiffersFromReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016003");
        orderRepository.save(createOrder(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("3")));
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000016103",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-30T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016205", "4", "2026-05-30T00:01:00Z");

        OrderReplayConsistencyResult result = service.check(orderId);

        assertFalse(result.consistent());
        assertEquals(
                List.of(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH),
                result.mismatchReasons()
        );
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.actualStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("3"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
    }

    @Test
    void returnsAllMismatchReasonsWhenStatusAndFilledQuantityDifferFromReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016004");
        orderRepository.save(createOrder(orderId, OrderStatus.ACKED, new BigDecimal("1")));
        saveExecutionEvent(
                orderId,
                "00000000-0000-0000-0000-000000016104",
                OrderExecutionEventType.ACKNOWLEDGED,
                "2026-05-30T00:00:00Z"
        );
        saveFillExecution(orderId, "00000000-0000-0000-0000-000000016206", "4", "2026-05-30T00:01:00Z");

        OrderReplayConsistencyResult result = service.check(orderId);

        assertFalse(result.consistent());
        assertEquals(
                List.of(
                        OrderReplayMismatchReason.STATUS_MISMATCH,
                        OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH
                ),
                result.mismatchReasons()
        );
        assertEquals(OrderStatus.ACKED, result.actualStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.replayedStatus());
        assertEquals(new BigDecimal("1"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.check(UUID.fromString("00000000-0000-0000-0000-000000016099"))
        );

        assertEquals("order not found", exception.getMessage());
    }

    private Order createOrder(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant createdAt = Instant.parse("2026-05-29T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000001101"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                filledQuantity,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                createdAt,
                createdAt
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
