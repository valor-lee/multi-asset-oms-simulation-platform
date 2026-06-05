package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderCancellationException;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderNotFoundException;
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

class OrderCancellationServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryOrderExecutionEventRepository eventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final OrderCancellationService service = new OrderCancellationService(
            orderRepository,
            eventRepository,
            fixedClock
    );

    @Test
    void requestsCancelForAckedOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001201"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        Order canceledOrder = service.requestCancel(order.orderId());

        assertEquals(OrderStatus.CANCEL_REQUESTED, canceledOrder.status());
        assertEquals(order.filledQuantity(), canceledOrder.filledQuantity());
        assertEquals(order.createdAt(), canceledOrder.createdAt());
        assertEquals(Instant.parse("2026-05-20T00:00:00Z"), canceledOrder.updatedAt());
    }

    @Test
    void requestsCancelForPartiallyFilledOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001202"),
                OrderStatus.PARTIALLY_FILLED,
                new BigDecimal("4")
        );
        orderRepository.save(order);

        Order canceledOrder = service.requestCancel(order.orderId());

        assertEquals(OrderStatus.CANCEL_REQUESTED, canceledOrder.status());
        assertEquals(new BigDecimal("4"), canceledOrder.filledQuantity());
    }

    @Test
    void returnsCancelRequestedOrderWhenCancelRequestIsRepeated() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001203"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);
        Order firstResult = service.requestCancel(order.orderId());

        Order secondResult = service.requestCancel(firstResult.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void confirmsCancelRequestedOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001204"),
                OrderStatus.CANCEL_REQUESTED,
                new BigDecimal("4")
        );
        orderRepository.save(order);

        Order canceledOrder = service.confirmCancel(order.orderId());

        assertEquals(OrderStatus.CANCELED, canceledOrder.status());
        assertEquals(new BigDecimal("4"), canceledOrder.filledQuantity());
        assertEquals(Instant.parse("2026-05-20T00:00:00Z"), canceledOrder.updatedAt());
    }

    @Test
    void returnsCanceledOrderWhenCancelConfirmationIsRepeated() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001205"),
                OrderStatus.CANCEL_REQUESTED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);
        Order firstResult = service.confirmCancel(order.orderId());

        Order secondResult = service.confirmCancel(firstResult.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void returnsCurrentOrderWhenCancelConfirmationEventIsRepeated() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001208"),
                OrderStatus.CANCEL_REQUESTED,
                new BigDecimal("4")
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000001308");
        orderRepository.save(order);

        Order firstResult = service.confirmCancel(order.orderId(), eventId);
        Order secondResult = service.confirmCancel(order.orderId(), eventId);

        assertEquals(firstResult, secondResult);
        assertEquals(OrderStatus.CANCELED, secondResult.status());
        assertEquals(firstResult.updatedAt(), secondResult.updatedAt());
    }

    @Test
    void rejectsCancelConfirmationEventIdAlreadyUsedByAnotherOrder() {
        Order firstOrder = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001209"),
                OrderStatus.CANCEL_REQUESTED,
                BigDecimal.ZERO
        );
        Order secondOrder = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001210"),
                OrderStatus.CANCEL_REQUESTED,
                BigDecimal.ZERO
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000001309");
        orderRepository.save(firstOrder);
        orderRepository.save(secondOrder);

        service.confirmCancel(firstOrder.orderId(), eventId);
        OrderCancellationException exception = assertThrows(
                OrderCancellationException.class,
                () -> service.confirmCancel(secondOrder.orderId(), eventId)
        );

        assertEquals("eventId belongs to another order", exception.getMessage());
    }

    @Test
    void rejectsCancelConfirmationEventIdAlreadyUsedByAnotherExecutionEventType() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001212"),
                OrderStatus.CANCEL_REQUESTED,
                BigDecimal.ZERO
        );
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000001312");
        orderRepository.save(order);
        eventRepository.save(new OrderExecutionEvent(
                eventId,
                order.orderId(),
                OrderExecutionEventType.ACKNOWLEDGED,
                Instant.parse("2026-05-19T00:00:00Z")
        ));

        OrderCancellationException exception = assertThrows(
                OrderCancellationException.class,
                () -> service.confirmCancel(order.orderId(), eventId)
        );

        assertEquals("eventId belongs to another execution event type", exception.getMessage());
    }

    @Test
    void rejectsCancelRequestForFilledOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001206"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);

        OrderCancellationException exception = assertThrows(
                OrderCancellationException.class,
                () -> service.requestCancel(order.orderId())
        );

        assertEquals("only ACKED or PARTIALLY_FILLED orders can be canceled", exception.getMessage());
    }

    @Test
    void rejectsCancelConfirmationForNonCancelRequestedOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001207"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderCancellationException exception = assertThrows(
                OrderCancellationException.class,
                () -> service.confirmCancel(order.orderId())
        );

        assertEquals("only CANCEL_REQUESTED orders can be confirmed canceled", exception.getMessage());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.requestCancel(UUID.fromString("00000000-0000-0000-0000-000000001299"))
        );

        assertEquals("order not found", exception.getMessage());
    }

    @Test
    void rejectsMissingCancelConfirmationEventId() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001211"),
                OrderStatus.CANCEL_REQUESTED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderCancellationException exception = assertThrows(
                OrderCancellationException.class,
                () -> service.confirmCancel(order.orderId(), null)
        );

        assertEquals("eventId is required", exception.getMessage());
    }

    private Order createOrder(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000001301"),
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
