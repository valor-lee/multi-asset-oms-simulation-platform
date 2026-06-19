package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.ExecutionRequestException;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateOpenOrderQueryServiceTest {

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final DuplicateOpenOrderQueryService service = new DuplicateOpenOrderQueryService(orderRepository);

    @Test
    void findsMatchingCreatedOrderAsDuplicateOpenOrder() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000060001"),
                UUID.fromString("00000000-0000-0000-0000-000000061001"),
                OrderStatus.CREATED
        );
        orderRepository.save(order);

        DuplicateOpenOrderResult result = service.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10.00"),
                new BigDecimal("55000.0"),
                TimeInForce.DAY,
                null
        );

        assertTrue(result.duplicateOpenOrderExists());
        assertEquals(order.orderId(), result.duplicateOpenOrderId());
        assertEquals(order, result.duplicateOpenOrder());
    }

    @Test
    void ignoresClosedOrders() {
        orderRepository.save(order(
                UUID.fromString("00000000-0000-0000-0000-000000060002"),
                UUID.fromString("00000000-0000-0000-0000-000000061002"),
                OrderStatus.FILLED
        ));

        DuplicateOpenOrderResult result = service.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                null
        );

        assertFalse(result.duplicateOpenOrderExists());
        assertNull(result.duplicateOpenOrderId());
        assertNull(result.duplicateOpenOrder());
    }

    @Test
    void excludesOrderWithGivenIntentId() {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000061003");
        orderRepository.save(order(
                UUID.fromString("00000000-0000-0000-0000-000000060003"),
                intentId,
                OrderStatus.ACKED
        ));

        DuplicateOpenOrderResult result = service.findDuplicateOpenOrder(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                intentId
        );

        assertFalse(result.duplicateOpenOrderExists());
    }

    @Test
    void rejectsMissingLimitPriceForLimitOrder() {
        ExecutionRequestException exception = assertThrows(
                ExecutionRequestException.class,
                () -> service.findDuplicateOpenOrder(
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("10"),
                        null,
                        TimeInForce.DAY,
                        null
                )
        );

        assertEquals("limitPrice is required for LIMIT orders", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        ExecutionRequestException exception = assertThrows(
                ExecutionRequestException.class,
                () -> service.findDuplicateOpenOrder(
                        "portfolio-1",
                        "005930",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        BigDecimal.ZERO,
                        new BigDecimal("55000"),
                        TimeInForce.DAY,
                        null
                )
        );

        assertEquals("quantity must be greater than zero", exception.getMessage());
    }

    private Order order(UUID orderId, UUID intentId, OrderStatus status) {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new Order(
                orderId,
                intentId,
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                now,
                now
        );
    }
}
