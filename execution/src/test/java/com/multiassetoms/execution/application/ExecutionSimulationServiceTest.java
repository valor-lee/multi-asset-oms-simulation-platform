package com.multiassetoms.execution.application;

import com.multiassetoms.execution.infrastructure.InMemoryExecutionSimulationRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderExecutionEventRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderFillExecutionRepository;
import com.multiassetoms.execution.infrastructure.InMemoryOrderRepository;
import com.multiassetoms.execution.model.ExecutionSimulationException;
import com.multiassetoms.execution.model.ExecutionSimulationResult;
import com.multiassetoms.execution.model.ExecutionSimulationStatus;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.infrastructure.InMemoryMarketPriceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionSimulationServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-24T00:00:00Z"),
            ZoneOffset.UTC
    );
    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryExecutionSimulationRepository simulationRepository =
            new InMemoryExecutionSimulationRepository();
    private final InMemoryOrderExecutionEventRepository eventRepository =
            new InMemoryOrderExecutionEventRepository();
    private final InMemoryOrderFillExecutionRepository fillExecutionRepository =
            new InMemoryOrderFillExecutionRepository();
    private final MarketPriceService marketPriceService = new MarketPriceService(
            new InMemoryMarketPriceRepository(),
            fixedClock
    );
    private final OrderAcknowledgementService acknowledgementService =
            new OrderAcknowledgementService(orderRepository, eventRepository, fixedClock);
    private final OrderFillService fillService =
            new OrderFillService(orderRepository, fillExecutionRepository, fixedClock);
    private final ExecutionSimulationService service = new ExecutionSimulationService(
            orderRepository,
            simulationRepository,
            () -> 80L,
            () -> BigDecimal.ONE,
            marketPriceService,
            acknowledgementService,
            fillService
    );

    @Test
    void acknowledgesAndFillsSentMarketBuyOrderWithPositiveSlippage() {
        Order order = order(OrderStatus.SENT, OrderSide.BUY, OrderType.MARKET, null);
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("55000"),
                null
        );

        ExecutionSimulationResult result = service.simulate(
                order.orderId(),
                UUID.fromString("00000000-0000-0000-0000-000000071001"),
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );

        assertEquals(ExecutionSimulationStatus.FILLED, result.simulationStatus());
        assertEquals(new BigDecimal("55000"), result.referencePrice());
        assertEquals(new BigDecimal("55550"), result.fillPrice());
        assertEquals(80L, result.delayMillis());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.order().status());
        assertEquals(new BigDecimal("4"), result.order().filledQuantity());
        assertEquals(1, eventRepository.findByOrderId(order.orderId()).size());
    }

    @Test
    void appliesNegativePriceDirectionForMarketSellOrder() {
        Order order = order(OrderStatus.ACKED, OrderSide.SELL, OrderType.MARKET, null);
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("55000"),
                null
        );

        ExecutionSimulationResult result = service.simulate(
                order.orderId(),
                UUID.fromString("00000000-0000-0000-0000-000000071002"),
                new BigDecimal("10"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );

        assertEquals(new BigDecimal("54450"), result.fillPrice());
        assertEquals(OrderStatus.FILLED, result.order().status());
        assertEquals(0, eventRepository.findByOrderId(order.orderId()).size());
    }

    @Test
    void fillsLimitBuyOrderWhenLatestPriceMeetsLimit() {
        Order order = order(
                OrderStatus.ACKED,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("55000")
        );
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("54500"),
                null
        );

        ExecutionSimulationResult result = service.simulate(
                order.orderId(),
                UUID.fromString("00000000-0000-0000-0000-000000071003"),
                new BigDecimal("10"),
                new BigDecimal("0.03"),
                BigDecimal.ZERO
        );

        assertEquals(ExecutionSimulationStatus.FILLED, result.simulationStatus());
        assertEquals(new BigDecimal("54500"), result.fillPrice());
    }

    @Test
    void leavesLimitBuyOrderAckedWhenLatestPriceExceedsLimit() {
        Order order = order(
                OrderStatus.SENT,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("55000")
        );
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("56000"),
                null
        );

        ExecutionSimulationResult result = service.simulate(
                order.orderId(),
                UUID.fromString("00000000-0000-0000-0000-000000071004"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertEquals(ExecutionSimulationStatus.NOT_FILLED, result.simulationStatus());
        assertNull(result.fillPrice());
        assertEquals(OrderStatus.ACKED, result.order().status());
        assertEquals(BigDecimal.ZERO, result.order().filledQuantity());
        assertEquals(0, fillExecutionRepository.findByOrderId(order.orderId()).size());
    }

    @Test
    void returnsStoredResultForSameSimulationRequest() {
        Order order = order(OrderStatus.ACKED, OrderSide.BUY, OrderType.MARKET, null);
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("55000"),
                null
        );
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000071005");

        ExecutionSimulationResult first = service.simulate(
                order.orderId(),
                simulationId,
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("60000"),
                null
        );
        ExecutionSimulationResult duplicate = service.simulate(
                order.orderId(),
                simulationId,
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );

        assertEquals(first, duplicate);
        assertEquals(new BigDecimal("4"), duplicate.order().filledQuantity());
        assertEquals(1, fillExecutionRepository.findByOrderId(order.orderId()).size());
    }

    @Test
    void rejectsSimulationIdReusedWithDifferentPayload() {
        Order order = order(OrderStatus.ACKED, OrderSide.BUY, OrderType.MARKET, null);
        orderRepository.save(order);
        marketPriceService.upsertLatestPrice(
                order.instrumentId(),
                new BigDecimal("55000"),
                null
        );
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000071006");
        service.simulate(
                order.orderId(),
                simulationId,
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO
        );

        ExecutionSimulationException exception = assertThrows(
                ExecutionSimulationException.class,
                () -> service.simulate(
                        order.orderId(),
                        simulationId,
                        new BigDecimal("5"),
                        new BigDecimal("0.01"),
                        BigDecimal.ZERO
                )
        );

        assertEquals(
                "simulationId was already used with another request",
                exception.getMessage()
        );
    }

    @Test
    void rejectsOrderInNonSimulatableStatus() {
        Order order = order(OrderStatus.CREATED, OrderSide.BUY, OrderType.MARKET, null);
        orderRepository.save(order);

        ExecutionSimulationException exception = assertThrows(
                ExecutionSimulationException.class,
                () -> service.simulate(
                        order.orderId(),
                        UUID.fromString("00000000-0000-0000-0000-000000071007"),
                        new BigDecimal("1"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );

        assertEquals(
                "only SENT, ACKED, or PARTIALLY_FILLED orders can be simulated",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSentOrderWhenBrokerRejectRateMatches() {
        ExecutionSimulationService rejectingService = new ExecutionSimulationService(
                orderRepository,
                simulationRepository,
                () -> 120L,
                () -> BigDecimal.ZERO,
                marketPriceService,
                acknowledgementService,
                fillService
        );
        Order order = order(OrderStatus.SENT, OrderSide.BUY, OrderType.MARKET, null);
        orderRepository.save(order);

        ExecutionSimulationResult result = rejectingService.simulate(
                order.orderId(),
                UUID.fromString("00000000-0000-0000-0000-000000071008"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                new BigDecimal("1.0")
        );

        assertEquals(ExecutionSimulationStatus.REJECTED, result.simulationStatus());
        assertNull(result.referencePrice());
        assertNull(result.fillPrice());
        assertEquals(new BigDecimal("1.0"), result.rejectRate());
        assertEquals(120L, result.delayMillis());
        assertEquals(OrderStatus.REJECTED, result.order().status());
        assertEquals(1, eventRepository.findByOrderId(order.orderId()).size());
        assertEquals(0, fillExecutionRepository.findByOrderId(order.orderId()).size());
    }

    private Order order(
            OrderStatus status,
            OrderSide side,
            OrderType orderType,
            BigDecimal limitPrice
    ) {
        return new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "portfolio-1",
                "005930",
                side,
                orderType,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                limitPrice,
                TimeInForce.DAY,
                status,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z")
        );
    }
}
