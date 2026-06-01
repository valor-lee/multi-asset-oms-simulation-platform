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

class OrderReplayConsistencyQueryServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-01T01:00:00Z"),
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
    private final OrderReplayConsistencyService consistencyService = new OrderReplayConsistencyService(fixedClock);
    private final OrderReplayConsistencyQueryService service = new OrderReplayConsistencyQueryService(
            orderRepository,
            replayService,
            consistencyService
    );

    @Test
    void checksStoredOrderAgainstReplayResult() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000023001");
        orderRepository.save(createOrder(orderId, OrderStatus.PARTIALLY_FILLED, new BigDecimal("3")));
        executionEventRepository.save(new OrderExecutionEvent(
                UUID.fromString("00000000-0000-0000-0000-000000023101"),
                orderId,
                OrderExecutionEventType.ACKNOWLEDGED,
                Instant.parse("2026-06-01T00:00:00Z")
        ));
        fillExecutionRepository.save(new OrderFillExecution(
                UUID.fromString("00000000-0000-0000-0000-000000023201"),
                orderId,
                new BigDecimal("4"),
                new BigDecimal("55000"),
                null,
                null,
                Instant.parse("2026-06-01T00:01:00Z")
        ));

        OrderReplayConsistencyResult result = service.checkStoredOrder(orderId);

        assertFalse(result.consistent());
        assertEquals(
                List.of(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH),
                result.mismatchReasons()
        );
        assertEquals(new BigDecimal("3"), result.actualFilledQuantity());
        assertEquals(new BigDecimal("4"), result.replayedFilledQuantity());
        assertEquals(2, result.appliedEventCount());
        assertEquals(Instant.parse("2026-06-01T01:00:00Z"), result.checkedAt());
    }

    @Test
    void rejectsMissingOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000023099");

        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.checkStoredOrder(orderId)
        );

        assertEquals("order not found", exception.getMessage());
    }

    private Order createOrder(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant createdAt = Instant.parse("2026-06-01T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000023901"),
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
}
