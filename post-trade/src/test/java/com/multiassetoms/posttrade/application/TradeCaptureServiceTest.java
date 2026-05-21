package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.posttrade.infrastructure.InMemoryTradeRepository;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeCaptureException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeCaptureServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-21T01:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final TradeCaptureService service = new TradeCaptureService(
            orderRepository,
            tradeRepository,
            fixedClock
    );

    @Test
    void capturesFilledOrderAsTrade() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005001"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);

        Trade trade = service.capture(order.orderId());

        assertEquals(order.orderId(), trade.orderId());
        assertEquals(order.intentId(), trade.intentId());
        assertEquals(order.portfolioId(), trade.portfolioId());
        assertEquals(order.instrumentId(), trade.instrumentId());
        assertEquals(order.side(), trade.side());
        assertEquals(new BigDecimal("10"), trade.quantity());
        assertEquals(TradeStatus.CAPTURED, trade.status());
        assertEquals(Instant.parse("2026-05-21T01:00:00Z"), trade.capturedAt());
        assertEquals(trade, tradeRepository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void capturesPartiallyFilledCanceledOrderAsTrade() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005002"),
                OrderStatus.CANCELED,
                new BigDecimal("4")
        );
        orderRepository.save(order);

        Trade trade = service.capture(order.orderId());

        assertEquals(new BigDecimal("4"), trade.quantity());
        assertEquals(TradeStatus.CAPTURED, trade.status());
    }

    @Test
    void returnsExistingTradeWhenCaptureIsRequestedAgain() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005003"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);

        Trade firstResult = service.capture(order.orderId());
        Trade secondResult = service.capture(order.orderId());

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.capturedAt(), secondResult.capturedAt());
    }

    @Test
    void rejectsCanceledOrderWithoutAnyFill() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005004"),
                OrderStatus.CANCELED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        TradeCaptureException exception = assertThrows(
                TradeCaptureException.class,
                () -> service.capture(order.orderId())
        );

        assertEquals(
                "only FILLED or partially filled CANCELED orders can be captured",
                exception.getMessage()
        );
    }

    @Test
    void rejectsOpenOrder() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005005"),
                OrderStatus.ACKED,
                BigDecimal.ZERO
        );
        orderRepository.save(order);

        TradeCaptureException exception = assertThrows(
                TradeCaptureException.class,
                () -> service.capture(order.orderId())
        );

        assertEquals(
                "only FILLED or partially filled CANCELED orders can be captured",
                exception.getMessage()
        );
    }

    @Test
    void rejectsFilledOrderWithInconsistentFilledQuantity() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005006"),
                OrderStatus.FILLED,
                new BigDecimal("9")
        );
        orderRepository.save(order);

        TradeCaptureException exception = assertThrows(
                TradeCaptureException.class,
                () -> service.capture(order.orderId())
        );

        assertEquals("filled order quantity is inconsistent", exception.getMessage());
    }

    @Test
    void rejectsMissingOrderId() {
        TradeCaptureException exception = assertThrows(
                TradeCaptureException.class,
                () -> service.capture(UUID.fromString("00000000-0000-0000-0000-000000005099"))
        );

        assertEquals("order not found", exception.getMessage());
    }

    private Order createOrder(UUID orderId, OrderStatus status, BigDecimal filledQuantity) {
        Instant createdAt = Instant.parse("2026-05-17T00:00:00Z");
        return new Order(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000005101"),
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
