package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillException;
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

class OrderFillServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-19T01:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final OrderFillService service = new OrderFillService(
            orderRepository,
            fillExecutionRepository,
            fixedClock
    );

    @Test
    void partiallyFillsAckedOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001001"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        Order filledOrder = service.fill(order.orderId(), new BigDecimal("4"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, filledOrder.status());
        assertEquals(new BigDecimal("4"), filledOrder.filledQuantity());
        assertEquals(order.createdAt(), filledOrder.createdAt());
        assertEquals(Instant.parse("2026-05-19T01:00:00Z"), filledOrder.updatedAt());
    }

    @Test
    void fullyFillsAckedOrderWhenFillQuantityEqualsOrderQuantity() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001002"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        Order filledOrder = service.fill(order.orderId(), new BigDecimal("10"));

        assertEquals(OrderStatus.FILLED, filledOrder.status());
        assertEquals(new BigDecimal("10"), filledOrder.filledQuantity());
    }

    @Test
    void accumulatesAdditionalFillForPartiallyFilledOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001003"),
                OrderStatus.PARTIALLY_FILLED,
                new BigDecimal("4")
        );
        orderRepository.save(order);

        Order filledOrder = service.fill(order.orderId(), new BigDecimal("6"));

        assertEquals(OrderStatus.FILLED, filledOrder.status());
        assertEquals(new BigDecimal("10"), filledOrder.filledQuantity());
    }

    @Test
    void returnsCurrentOrderWithoutAccumulatingDuplicateFillExecution() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001008"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000002001");
        orderRepository.save(order);

        Order firstResult = service.fill(order.orderId(), fillExecutionId, new BigDecimal("4"));
        Order duplicateResult = service.fill(order.orderId(), fillExecutionId, new BigDecimal("4"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, firstResult.status());
        assertEquals(new BigDecimal("4"), firstResult.filledQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, duplicateResult.status());
        assertEquals(new BigDecimal("4"), duplicateResult.filledQuantity());
    }

    @Test
    void storesFillPriceWhenFillPriceIsProvided() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001012"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000002003");
        orderRepository.save(order);

        service.fill(order.orderId(), fillExecutionId, new BigDecimal("4"), new BigDecimal("55000"));

        assertEquals(
                new BigDecimal("55000"),
                fillExecutionRepository.findByFillExecutionId(fillExecutionId)
                        .orElseThrow()
                        .fillPrice()
        );
    }

    @Test
    void rejectsFillExecutionIdAlreadyUsedByAnotherOrder() {
        Order firstOrder = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001009"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        Order secondOrder = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001010"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        UUID fillExecutionId = UUID.fromString("00000000-0000-0000-0000-000000002002");
        orderRepository.save(firstOrder);
        orderRepository.save(secondOrder);

        service.fill(firstOrder.orderId(), fillExecutionId, new BigDecimal("4"));
        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(secondOrder.orderId(), fillExecutionId, new BigDecimal("4"))
        );

        assertEquals("fillExecutionId belongs to another order", exception.getMessage());
    }

    @Test
    void rejectsFillThatExceedsOrderQuantity() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001004"),
                OrderStatus.PARTIALLY_FILLED,
                new BigDecimal("8")
        );
        orderRepository.save(order);

        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(order.orderId(), new BigDecimal("3"))
        );

        assertEquals("filled quantity exceeds order quantity", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveFillQuantity() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001005"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(order.orderId(), BigDecimal.ZERO)
        );

        assertEquals("fillQuantity must be greater than zero", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveFillPrice() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001013"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(
                        order.orderId(),
                        UUID.fromString("00000000-0000-0000-0000-000000002004"),
                        new BigDecimal("1"),
                        BigDecimal.ZERO
                )
        );

        assertEquals("fillPrice must be greater than zero", exception.getMessage());
    }

    @Test
    void rejectsFillForNonFillableOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001006"),
                OrderStatus.SENT,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(order.orderId(), new BigDecimal("1"))
        );

        assertEquals("only ACKED, PARTIALLY_FILLED, or CANCEL_REQUESTED orders can be filled", exception.getMessage());
    }

    @Test
    void fillsCancelRequestedOrderWhenFillArrivesBeforeCancelConfirmation() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001007"),
                OrderStatus.CANCEL_REQUESTED,
                new BigDecimal("4")
        );
        orderRepository.save(order);

        Order filledOrder = service.fill(order.orderId(), new BigDecimal("6"));

        assertEquals(OrderStatus.FILLED, filledOrder.status());
        assertEquals(new BigDecimal("10"), filledOrder.filledQuantity());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(
                        UUID.fromString("00000000-0000-0000-0000-000000001099"),
                        new BigDecimal("1")
                )
        );

        assertEquals("order not found", exception.getMessage());
    }

    @Test
    void rejectsMissingFillExecutionId() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000001011"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        OrderFillException exception = assertThrows(
                OrderFillException.class,
                () -> service.fill(order.orderId(), null, new BigDecimal("1"))
        );

        assertEquals("fillExecutionId is required", exception.getMessage());
    }

    private Order createOrder(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
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
}
