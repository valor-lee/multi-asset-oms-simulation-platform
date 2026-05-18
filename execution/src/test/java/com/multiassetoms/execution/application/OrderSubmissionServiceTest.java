package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.execution.model.OrderSubmissionException;
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

class OrderSubmissionServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final OrderSubmissionService service = new OrderSubmissionService(orderRepository, fixedClock);

    @Test
    void submitsCreatedOrderAndStoresSentStatus() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000601"),
                OrderStatus.CREATED
        );
        orderRepository.save(order);

        Order submittedOrder = service.submit(order.orderId());

        assertEquals(OrderStatus.SENT, submittedOrder.status());
        assertEquals(order.createdAt(), submittedOrder.createdAt());
        assertEquals(Instant.parse("2026-05-18T00:00:00Z"), submittedOrder.updatedAt());
        assertEquals(OrderStatus.SENT, orderRepository.findByOrderId(order.orderId()).orElseThrow().status());
    }

    @Test
    void returnsSentOrderWhenSubmitIsRequestedAgain() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000602"),
                OrderStatus.CREATED
        );
        orderRepository.save(order);
        Order firstResult = service.submit(order.orderId());

        Order secondResult = service.submit(firstResult.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderSubmissionException exception = assertThrows(
                OrderSubmissionException.class,
                () -> service.submit(UUID.fromString("00000000-0000-0000-0000-000000000699"))
        );

        assertEquals("order not found", exception.getMessage());
    }

    private Order order(UUID orderId, OrderStatus status) {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                createdAt,
                createdAt
        );
    }
}
