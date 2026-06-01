package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderExecutionReplayQueryServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-01T00:00:00Z"),
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
    private final OrderExecutionReplayQueryService service = new OrderExecutionReplayQueryService(
            orderRepository,
            replayService
    );

    @Test
    void replaysUsingStoredOrderQuantity() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000022001");
        orderRepository.save(createOrder(orderId, new BigDecimal("12")));
        executionEventRepository.save(new OrderExecutionEvent(
                UUID.fromString("00000000-0000-0000-0000-000000022101"),
                orderId,
                OrderExecutionEventType.ACKNOWLEDGED,
                Instant.parse("2026-06-01T00:01:00Z")
        ));
        fillExecutionRepository.save(new OrderFillExecution(
                UUID.fromString("00000000-0000-0000-0000-000000022201"),
                orderId,
                new BigDecimal("12"),
                new BigDecimal("55000"),
                null,
                null,
                Instant.parse("2026-06-01T00:02:00Z")
        ));

        OrderReplayResult result = service.replayStoredOrder(orderId);

        assertEquals(orderId, result.orderId());
        assertEquals(new BigDecimal("12"), result.orderQuantity());
        assertEquals(new BigDecimal("12"), result.replayedFilledQuantity());
        assertEquals(OrderStatus.FILLED, result.replayedStatus());
        assertEquals(2, result.appliedEventCount());
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), result.replayedAt());
    }

    @Test
    void rejectsMissingOrder() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000022099");

        OrderReplayException exception = assertThrows(
                OrderReplayException.class,
                () -> service.replayStoredOrder(orderId)
        );

        assertEquals("order not found", exception.getMessage());
    }

    private Order createOrder(UUID orderId, BigDecimal quantity) {
        Instant createdAt = Instant.parse("2026-06-01T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000022901"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                quantity,
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                OrderStatus.SENT,
                createdAt,
                createdAt
        );
    }
}
