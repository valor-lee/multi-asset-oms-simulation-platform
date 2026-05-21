package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderAcknowledgementException;
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

class OrderAcknowledgementServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryOrderExecutionEventRepository eventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final OrderAcknowledgementService service = new OrderAcknowledgementService(
            orderRepository,
            eventRepository,
            fixedClock
    );

    @Test
    void acknowledgesSentOrderAndStoresAckedStatus() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000801"),
                OrderStatus.SENT
        );
        orderRepository.save(order);

        Order acknowledgedOrder = service.acknowledge(order.orderId());

        assertEquals(OrderStatus.ACKED, acknowledgedOrder.status());
        assertEquals(order.createdAt(), acknowledgedOrder.createdAt());
        assertEquals(Instant.parse("2026-05-19T00:00:00Z"), acknowledgedOrder.updatedAt());
        assertEquals(OrderStatus.ACKED, orderRepository.findByOrderId(order.orderId()).orElseThrow().status());
    }

    @Test
    void returnsAckedOrderWhenAcknowledgeIsRequestedAgain() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                OrderStatus.SENT
        );
        orderRepository.save(order);
        Order firstResult = service.acknowledge(order.orderId());

        Order secondResult = service.acknowledge(firstResult.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void returnsCurrentOrderWhenAcknowledgeEventIsRepeated() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000806"),
                OrderStatus.SENT
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000906");
        orderRepository.save(order);

        Order firstResult = service.acknowledge(order.orderId(), eventId);
        Order secondResult = service.acknowledge(order.orderId(), eventId);

        assertEquals(firstResult, secondResult);
        assertEquals(OrderStatus.ACKED, secondResult.status());
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void rejectsSentOrderAndStoresRejectedStatus() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                OrderStatus.SENT
        );
        orderRepository.save(order);

        Order rejectedOrder = service.reject(order.orderId());

        assertEquals(OrderStatus.REJECTED, rejectedOrder.status());
        assertEquals(order.createdAt(), rejectedOrder.createdAt());
        assertEquals(Instant.parse("2026-05-19T00:00:00Z"), rejectedOrder.updatedAt());
        assertEquals(OrderStatus.REJECTED, orderRepository.findByOrderId(order.orderId()).orElseThrow().status());
    }

    @Test
    void returnsRejectedOrderWhenRejectIsRequestedAgain() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000804"),
                OrderStatus.SENT
        );
        orderRepository.save(order);
        Order firstResult = service.reject(order.orderId());

        Order secondResult = service.reject(firstResult.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void returnsCurrentOrderWhenRejectEventIsRepeated() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000807"),
                OrderStatus.SENT
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000907");
        orderRepository.save(order);

        Order firstResult = service.reject(order.orderId(), eventId);
        Order secondResult = service.reject(order.orderId(), eventId);

        assertEquals(firstResult, secondResult);
        assertEquals(OrderStatus.REJECTED, secondResult.status());
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void rejectsEventIdAlreadyUsedByAnotherOrder() {
        Order firstOrder = order(
                UUID.fromString("00000000-0000-0000-0000-000000000808"),
                OrderStatus.SENT
        );
        Order secondOrder = order(
                UUID.fromString("00000000-0000-0000-0000-000000000809"),
                OrderStatus.SENT
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000908");
        orderRepository.save(firstOrder);
        orderRepository.save(secondOrder);

        service.acknowledge(firstOrder.orderId(), eventId);
        OrderAcknowledgementException exception = assertThrows(
                OrderAcknowledgementException.class,
                () -> service.acknowledge(secondOrder.orderId(), eventId)
        );

        assertEquals("eventId belongs to another order", exception.getMessage());
    }

    @Test
    void rejectsEventIdAlreadyUsedByAnotherExecutionEventType() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000810"),
                OrderStatus.SENT
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000910");
        orderRepository.save(order);

        service.acknowledge(order.orderId(), eventId);
        OrderAcknowledgementException exception = assertThrows(
                OrderAcknowledgementException.class,
                () -> service.reject(order.orderId(), eventId)
        );

        assertEquals("eventId belongs to another execution event type", exception.getMessage());
    }

    @Test
    void rejectsAcknowledgeForNonSentOrder() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000805"),
                OrderStatus.CREATED
        );
        orderRepository.save(order);

        OrderAcknowledgementException exception = assertThrows(
                OrderAcknowledgementException.class,
                () -> service.acknowledge(order.orderId())
        );

        assertEquals("only SENT orders can be acknowledged or rejected", exception.getMessage());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderAcknowledgementException exception = assertThrows(
                OrderAcknowledgementException.class,
                () -> service.acknowledge(UUID.fromString("00000000-0000-0000-0000-000000000899"))
        );

        assertEquals("order not found", exception.getMessage());
    }

    @Test
    void rejectsMissingEventId() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000811"),
                OrderStatus.SENT
        );
        orderRepository.save(order);

        OrderAcknowledgementException exception = assertThrows(
                OrderAcknowledgementException.class,
                () -> service.acknowledge(order.orderId(), null)
        );

        assertEquals("eventId is required", exception.getMessage());
    }

    private Order order(UUID orderId, OrderStatus status) {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("55000"),
                TimeInForce.DAY,
                status,
                createdAt,
                createdAt
        );
    }
}
