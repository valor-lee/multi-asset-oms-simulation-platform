package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillExecution;
import com.multiassetoms.execution.model.OrderNotFoundException;
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
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final InMemoryTradeRepository tradeRepository = new InMemoryTradeRepository();
    private final TradeCaptureService service = new TradeCaptureService(
            orderRepository,
            fillExecutionRepository,
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
        assertEquals(null, trade.averageFillPrice());
        assertEquals(null, trade.grossNotional());
        assertEquals(TradeStatus.CAPTURED, trade.status());
        assertEquals(Instant.parse("2026-05-21T01:00:00Z"), trade.capturedAt());
        assertEquals(trade, tradeRepository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void capturesAverageFillPriceAndGrossNotionalWhenAllFillPricesExist() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005007"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005201", "4", "55000");
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005202", "6", "55500");

        Trade trade = service.capture(order.orderId());

        assertEquals(new BigDecimal("55300.0000000000"), trade.averageFillPrice());
        assertEquals(new BigDecimal("553000"), trade.grossNotional());
    }

    @Test
    void capturesFeeAmountWhenAllPricedFillsHaveFeeAmount() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005009"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005205", "4", "55000", "40");
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005206", "6", "55500", "60");

        Trade trade = service.capture(order.orderId());

        assertEquals(new BigDecimal("100"), trade.feeAmount());
    }

    @Test
    void capturesTaxAmountWhenAllPricedFillsHaveTaxAmount() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005010"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005207", "4", "55000", "40", "12");
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005208", "6", "55500", "60", "18");

        Trade trade = service.capture(order.orderId());

        assertEquals(new BigDecimal("30"), trade.taxAmount());
    }

    @Test
    void leavesFillPriceSummaryEmptyWhenAnyFillPriceIsMissing() {
        Order order = createOrder(
                UUID.fromString("00000000-0000-0000-0000-000000005008"),
                OrderStatus.FILLED,
                new BigDecimal("10")
        );
        orderRepository.save(order);
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005203", "4", "55000");
        saveFillExecution(order.orderId(), "00000000-0000-0000-0000-000000005204", "6", null);

        Trade trade = service.capture(order.orderId());

        assertEquals(null, trade.averageFillPrice());
        assertEquals(null, trade.grossNotional());
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
        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
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

    private void saveFillExecution(
            UUID orderId,
            String fillExecutionId,
            String fillQuantity,
            String fillPrice
    ) {
        saveFillExecution(orderId, fillExecutionId, fillQuantity, fillPrice, null);
    }

    private void saveFillExecution(
            UUID orderId,
            String fillExecutionId,
            String fillQuantity,
            String fillPrice,
            String feeAmount
    ) {
        saveFillExecution(orderId, fillExecutionId, fillQuantity, fillPrice, feeAmount, null);
    }

    private void saveFillExecution(
            UUID orderId,
            String fillExecutionId,
            String fillQuantity,
            String fillPrice,
            String feeAmount,
            String taxAmount
    ) {
        fillExecutionRepository.save(new OrderFillExecution(
                UUID.fromString(fillExecutionId),
                orderId,
                new BigDecimal(fillQuantity),
                fillPrice == null ? null : new BigDecimal(fillPrice),
                feeAmount == null ? null : new BigDecimal(feeAmount),
                taxAmount == null ? null : new BigDecimal(taxAmount),
                Instant.parse("2026-05-20T01:00:00Z")
        ));
    }
}
