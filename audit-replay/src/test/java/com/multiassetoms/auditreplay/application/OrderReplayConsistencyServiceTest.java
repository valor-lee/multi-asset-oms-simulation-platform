package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayMismatchReason;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.model.Order;
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
    private final OrderReplayConsistencyService service = new OrderReplayConsistencyService(fixedClock);

    @Test
    void returnsConsistentWhenOrderMatchesReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016001");
        Order order = createOrder(orderId, OrderStatus.FILLED, new BigDecimal("10"));
        OrderReplayResult replayResult = replayResult(orderId, OrderStatus.FILLED, "10", 3);

        OrderReplayConsistencyResult result = service.check(order, replayResult);

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
        Order order = createOrder(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("10"));
        OrderReplayResult replayResult = replayResult(orderId, OrderStatus.FILLED, "10", 3);

        OrderReplayConsistencyResult result = service.check(order, replayResult);

        assertFalse(result.consistent());
        assertEquals(
                List.of(OrderReplayMismatchReason.STATUS_MISMATCH),
                result.mismatchReasons()
        );
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.actualStatus());
        assertEquals(OrderStatus.FILLED, result.replayedStatus());
    }

    @Test
    void returnsInconsistentWhenFilledQuantityDiffersFromReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016003");
        Order order = createOrder(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("3"));
        OrderReplayResult replayResult = replayResult(orderId, OrderStatus.PARTIALLY_FILLED, "4", 2);

        OrderReplayConsistencyResult result = service.check(order, replayResult);

        assertFalse(result.consistent());
        assertEquals(
                List.of(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH),
                result.mismatchReasons()
        );
        assertEquals(new BigDecimal("3"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
    }

    @Test
    void returnsAllMismatchReasonsWhenStatusAndFilledQuantityDifferFromReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000016004");
        Order order = createOrder(orderId, OrderStatus.ACKED, new BigDecimal("1"));
        OrderReplayResult replayResult = replayResult(orderId, OrderStatus.PARTIALLY_FILLED, "4", 2);

        OrderReplayConsistencyResult result = service.check(order, replayResult);

        assertFalse(result.consistent());
        assertEquals(
                List.of(
                        OrderReplayMismatchReason.STATUS_MISMATCH,
                        OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH
                ),
                result.mismatchReasons()
        );
    }

    @Test
    void rejectsMismatchedOrderId() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000016005"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        OrderReplayResult replayResult = replayResult(
                UUID.fromString("00000000-0000-0000-0000-000000016006"),
                OrderStatus.FILLED,
                "10",
                3
        );

        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.check(order, replayResult)
        );

        assertEquals("orderId does not match replayResult", exception.getMessage());
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

    private OrderReplayResult replayResult(
            UUID orderId,
            OrderStatus replayedStatus,
            String replayedFilledQuantity,
            int appliedEventCount
    ) {
        return new OrderReplayResult(
                orderId,
                OrderStatus.SENT,
                replayedStatus,
                new BigDecimal("10"),
                new BigDecimal(replayedFilledQuantity),
                appliedEventCount,
                Instant.parse("2026-05-30T00:30:00Z")
        );
    }
}
