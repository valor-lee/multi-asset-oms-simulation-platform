package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.ExecutionSimulationDelay;
import com.multiassetoms.execution.application.port.ExecutionSimulationRandom;
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
    private final ExecutionSimulationRandom simulationRandom;
    private final MarketPriceService marketPriceService;
    private final OrderAcknowledgementService acknowledgementService;
    private final OrderFillService fillService;

    public ExecutionSimulationService(
            OrderRepository orderRepository,
            ExecutionSimulationRepository simulationRepository,
            ExecutionSimulationDelay simulationDelay,
            ExecutionSimulationRandom simulationRandom,
            MarketPriceService marketPriceService,
            OrderAcknowledgementService acknowledgementService,
            OrderFillService fillService
    ) {
        this.orderRepository = orderRepository;
        this.simulationRepository = simulationRepository;
        this.simulationDelay = simulationDelay;
        this.simulationRandom = simulationRandom;
        this.marketPriceService = marketPriceService;
        this.acknowledgementService = acknowledgementService;
        this.fillService = fillService;
    }

    /**
     * broker/exchange 역할을 대신해 ACK 지연, reject, 체결 가격을 시뮬레이션한다.
     * 같은 simulationId 재요청은 저장된 결과를 반환해 ACK/fill을 중복 반영하지 않는다.
     *
     * 1. 주문 상태 확인
     * 2. ACK 전 broker reject 확률 평가
     * 3. reject되지 않은 주문 ACK 처리
     * 4. 최신 가격 조회와 체결 가능 여부 판단
     * 5. fill 처리
     * 6. 결과 저장
     */
    public ExecutionSimulationResult simulate(
            UUID orderId,
            UUID simulationId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate,
            BigDecimal rejectRate
    ) {
        return simulate(orderId, simulationId, fillQuantity, slippageRate, rejectRate, null);
    }

    public synchronized ExecutionSimulationResult simulate(
            UUID orderId,
            UUID simulationId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate,
            BigDecimal rejectRate,
            BigDecimal availableQuantity
    ) {
        validateRequest(
                orderId,
                simulationId,
                fillQuantity,
                slippageRate,
                rejectRate,
                availableQuantity
        );

        ExecutionSimulationResult existingResult = simulationRepository
                .findBySimulationId(simulationId)
                .orElse(null);
        if (existingResult != null) {
            return existingResult(
                    orderId,
                    fillQuantity,
                    slippageRate,
                    rejectRate,
                    availableQuantity,
                    existingResult
            );
        }

        Order order = findOrder(orderId);
        validateSimulatable(order);

        long delayMillis = simulationDelay.await();
        if (shouldReject(order, rejectRate)) {
            Order rejectedOrder = reject(order, simulationId);
            return simulationRepository.save(new ExecutionSimulationResult(
                    simulationId,
                    orderId,
                    ExecutionSimulationStatus.REJECTED,
                    null,
                    null,
                    fillQuantity,
                    BigDecimal.ZERO,
                    availableQuantity,
                    slippageRate,
                    rejectRate,
                    delayMillis,
                    rejectedOrder
            ));
        }

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
                    BigDecimal.ZERO,
                    availableQuantity,
                    slippageRate,
                    rejectRate,
                    delayMillis,
                    acknowledgedOrder
            ));
        }

        BigDecimal executableQuantity = executableQuantity(fillQuantity, availableQuantity);
        if (executableQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return simulationRepository.save(new ExecutionSimulationResult(
                    simulationId,
                    orderId,
                    ExecutionSimulationStatus.NOT_FILLED,
                    referencePrice,
                    null,
                    fillQuantity,
                    BigDecimal.ZERO,
                    availableQuantity,
                    slippageRate,
                    rejectRate,
                    delayMillis,
                    acknowledgedOrder
            ));
        }

        BigDecimal fillPrice = simulatedFillPrice(acknowledgedOrder, referencePrice, slippageRate);
        Order filledOrder = fillService.fill(
                orderId,
                simulationId,
                executableQuantity,
                fillPrice
        );
        return simulationRepository.save(new ExecutionSimulationResult(
                simulationId,
                orderId,
                ExecutionSimulationStatus.FILLED,
                referencePrice,
                fillPrice,
                fillQuantity,
                executableQuantity,
                availableQuantity,
                slippageRate,
                rejectRate,
                delayMillis,
                filledOrder
        ));
    }

    private void validateRequest(
            UUID orderId,
            UUID simulationId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate,
            BigDecimal rejectRate,
            BigDecimal availableQuantity
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
        if (rejectRate == null
                || rejectRate.compareTo(BigDecimal.ZERO) < 0
                || rejectRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ExecutionSimulationException(
                    "rejectRate must be zero or greater and less than or equal to one"
            );
        }
        if (availableQuantity != null && availableQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ExecutionSimulationException(
                    "availableQuantity must be zero or greater"
            );
        }
    }

    private ExecutionSimulationResult existingResult(
            UUID orderId,
            BigDecimal fillQuantity,
            BigDecimal slippageRate,
            BigDecimal rejectRate,
            BigDecimal availableQuantity,
            ExecutionSimulationResult result
    ) {
        if (!result.orderId().equals(orderId)) {
            throw new ExecutionSimulationException("simulationId belongs to another order");
        }
        if (result.requestedFillQuantity().compareTo(fillQuantity) != 0
                || result.slippageRate().compareTo(slippageRate) != 0
                || result.rejectRate().compareTo(rejectRate) != 0
                || !sameNullableQuantity(result.availableQuantity(), availableQuantity)) {
            throw new ExecutionSimulationException(
                    "simulationId was already used with another request"
            );
        }
        return result;
    }

    private boolean sameNullableQuantity(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
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

    private boolean shouldReject(Order order, BigDecimal rejectRate) {
        if (order.status() != OrderStatus.SENT || rejectRate.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        return simulationRandom.nextUnitInterval().compareTo(rejectRate) < 0;
    }

    private Order reject(Order order, UUID simulationId) {
        UUID rejectEventId = UUID.nameUUIDFromBytes(
                ("execution-simulation-reject:" + simulationId).getBytes(StandardCharsets.UTF_8)
        );
        return acknowledgementService.reject(order.orderId(), rejectEventId);
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

    private BigDecimal executableQuantity(
            BigDecimal requestedFillQuantity,
            BigDecimal availableQuantity
    ) {
        if (availableQuantity == null) {
            return requestedFillQuantity;
        }
        return requestedFillQuantity.min(availableQuantity);
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
