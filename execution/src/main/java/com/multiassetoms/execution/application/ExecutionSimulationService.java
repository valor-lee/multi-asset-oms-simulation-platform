package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.ExecutionSimulationDelay;
import com.multiassetoms.execution.application.port.ExecutionSimulationRepository;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.ExecutionSimulationException;
import com.multiassetoms.execution.model.ExecutionSimulationResult;
import com.multiassetoms.execution.model.ExecutionSimulationStatus;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.model.MarketPrice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ExecutionSimulationService {

    private final OrderRepository orderRepository;
    private final ExecutionSimulationRepository simulationRepository;
    private final ExecutionSimulationDelay simulationDelay;
    private final MarketPriceService marketPriceService;
    private final OrderAcknowledgementService acknowledgementService;
    private final OrderFillService fillService;

    public ExecutionSimulationService(
            OrderRepository orderRepository,
            ExecutionSimulationRepository simulationRepository,
            ExecutionSimulationDelay simulationDelay,
            MarketPriceService marketPriceService,
            OrderAcknowledgementService acknowledgementService,
            OrderFillService fillService
    ) {
        this.orderRepository = orderRepository;
        this.simulationRepository = simulationRepository;
        this.simulationDelay = simulationDelay;
        this.marketPriceService = marketPriceService;
        this.acknowledgementService = acknowledgementService;
        this.fillService = fillService;
    }

    /**
     * 최신 시장 가격을 기준으로 ACK 지연과 체결 가격을 시뮬레이션한다.
     * 같은 simulationId 재요청은 저장된 결과를 반환해 ACK/fill을 중복 반영하지 않는다.
     *
     * 1. 주문 상태 확인
     * 2. 최신 가격 조회
     * 3. 체결 가능 여부
     * 4. 판단 ACK 처리
     * 5. fill 처리
     * 6. 결과 저장
     * 
     * 실제 거래소 역할
     */
    public synchronized ExecutionSimulationResult simulate(
            UUID orderId,
            UUID simulationId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate
    ) {
        validateRequest(orderId, simulationId, fillQuantity, slippageRate);

        ExecutionSimulationResult existingResult = simulationRepository
                .findBySimulationId(simulationId)
                .orElse(null);
        if (existingResult != null) {
            return existingResult(orderId, fillQuantity, slippageRate, existingResult);
        }

        Order order = findOrder(orderId);
        validateSimulatable(order);

        long delayMillis = simulationDelay.await();
        Order acknowledgedOrder = acknowledgeIfSent(order, simulationId);
        MarketPrice marketPrice = marketPriceService.latestPrice(order.instrumentId());
        BigDecimal referencePrice = marketPrice.price();

        if (!isExecutable(acknowledgedOrder, referencePrice)) {
            return simulationRepository.save(new ExecutionSimulationResult(
                    simulationId,
                    orderId,
                    ExecutionSimulationStatus.NOT_FILLED,
                    referencePrice,
                    null,
                    fillQuantity,
                    slippageRate,
                    delayMillis,
                    acknowledgedOrder
            ));
        }

        BigDecimal fillPrice = simulatedFillPrice(acknowledgedOrder, referencePrice, slippageRate);
        Order filledOrder = fillService.fill(
                orderId,
                simulationId,
                fillQuantity,
                fillPrice
        );
        return simulationRepository.save(new ExecutionSimulationResult(
                simulationId,
                orderId,
                ExecutionSimulationStatus.FILLED,
                referencePrice,
                fillPrice,
                fillQuantity,
                slippageRate,
                delayMillis,
                filledOrder
        ));
    }

    private void validateRequest(
            UUID orderId,
            UUID simulationId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate
    ) {
        if (orderId == null) {
            throw new ExecutionSimulationException("orderId is required");
        }
        if (simulationId == null) {
            throw new ExecutionSimulationException("simulationId is required");
        }
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExecutionSimulationException("fillQuantity must be greater than zero");
        }
        if (slippageRate == null
                || slippageRate.compareTo(BigDecimal.ZERO) < 0
                || slippageRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new ExecutionSimulationException(
                    "slippageRate must be zero or greater and less than one"
            );
        }
    }

    private ExecutionSimulationResult existingResult(
            UUID orderId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate,
            ExecutionSimulationResult result
    ) {
        if (!result.orderId().equals(orderId)) {
            throw new ExecutionSimulationException("simulationId belongs to another order");
        }
        if (result.requestedFillQuantity().compareTo(fillQuantity) != 0
                || result.slippageRate().compareTo(slippageRate) != 0) {
            throw new ExecutionSimulationException(
                    "simulationId was already used with another request"
            );
        }
        return result;
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("order not found"));
    }

    private void validateSimulatable(Order order) {
        if (order.status() != OrderStatus.SENT
                && order.status() != OrderStatus.ACKED
                && order.status() != OrderStatus.PARTIALLY_FILLED) {
            throw new ExecutionSimulationException(
                    "only SENT, ACKED, or PARTIALLY_FILLED orders can be simulated"
            );
        }
    }

    private Order acknowledgeIfSent(Order order, UUID simulationId) {
        if (order.status() != OrderStatus.SENT) {
            return order;
        }
        UUID acknowledgementEventId = UUID.nameUUIDFromBytes(
                ("execution-simulation-ack:" + simulationId).getBytes(StandardCharsets.UTF_8)
        );
        return acknowledgementService.acknowledge(order.orderId(), acknowledgementEventId);
    }

    private boolean isExecutable(Order order, BigDecimal referencePrice) {
        if (order.orderType() == OrderType.MARKET) {
            return true;
        }
        if (order.side() == OrderSide.BUY) {
            return referencePrice.compareTo(order.limitPrice()) <= 0;
        }
        return referencePrice.compareTo(order.limitPrice()) >= 0;
    }

    private BigDecimal simulatedFillPrice(
            Order order,
            BigDecimal referencePrice,
            BigDecimal slippageRate
    ) {
        if (order.orderType() == OrderType.LIMIT) {
            return referencePrice;
        }

        BigDecimal multiplier = order.side() == OrderSide.BUY
                ? BigDecimal.ONE.add(slippageRate)
                : BigDecimal.ONE.subtract(slippageRate);
        return referencePrice.multiply(multiplier)
                .setScale(referencePrice.scale(), RoundingMode.HALF_UP);
    }
}
