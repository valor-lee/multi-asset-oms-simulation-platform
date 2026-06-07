package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.application.port.OrderFillExecutionRepository;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderFillExecution;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeCaptureException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TradeCaptureService {

    private final OrderRepository orderRepository;
    private final OrderFillExecutionRepository fillExecutionRepository;
    private final TradeRepository tradeRepository;
    private final Clock clock;

    public TradeCaptureService(
            OrderRepository orderRepository,
            OrderFillExecutionRepository fillExecutionRepository,
            TradeRepository tradeRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.fillExecutionRepository = fillExecutionRepository;
        this.tradeRepository = tradeRepository;
        this.clock = clock;
    }

    /**
     * execution 완료 order를 post-trade trade로 캡처한다.
     * 상태별 처리:
     * - FILLED: 전체 체결 수량을 trade로 캡처
     * - CANCELED: 체결 수량이 있으면 부분 체결 trade로 캡처
     * - 이미 캡처된 order: 중복 요청으로 보고 기존 trade 반환
     * - 그 외 상태 또는 체결 수량이 없는 CANCELED: trade 캡처 대상이 아니므로 예외
     *
     * @param orderId trade로 캡처할 order id
     * @return 캡처되었거나 이미 존재하는 trade
     */
    public Trade capture(UUID orderId) {
        Trade existingTrade = tradeRepository.findByOrderId(orderId)
                .orElse(null);
        if (existingTrade != null) {
            return existingTrade;
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("order not found"));

        validateCapturable(order);
        return tradeRepository.save(toTrade(order, Instant.now(clock)));
    }

    private void validateCapturable(Order order) {
        if (order.status() == OrderStatus.FILLED) {
            validateFilledQuantity(order);
            return;
        }

        if (order.status() == OrderStatus.CANCELED
                && order.filledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }

        throw new TradeCaptureException(
                "only FILLED or partially filled CANCELED orders can be captured"
        );
    }

    private void validateFilledQuantity(Order order) {
        if (order.filledQuantity().compareTo(order.quantity()) != 0) {
            throw new TradeCaptureException("filled order quantity is inconsistent");
        }
    }

    private Trade toTrade(Order order, Instant capturedAt) {
        FillSummary fillSummary = summarizeFills(order);
        return new Trade(
                UUID.randomUUID(),
                order.orderId(),
                order.intentId(),
                order.portfolioId(),
                order.instrumentId(),
                order.side(),
                order.filledQuantity(),
                fillSummary.averageFillPrice(),
                fillSummary.grossNotional(),
                fillSummary.feeAmount(),
                fillSummary.taxAmount(),
                TradeStatus.CAPTURED,
                capturedAt,
                null,
                capturedAt
        );
    }

    private FillSummary summarizeFills(Order order) {
        List<OrderFillExecution> fillExecutions = fillExecutionRepository.findByOrderId(order.orderId());
        if (fillExecutions.isEmpty()) {
            return FillSummary.empty();
        }

        BigDecimal filledQuantity = BigDecimal.ZERO;
        BigDecimal grossNotional = BigDecimal.ZERO;
        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        boolean hasMissingFee = false;
        boolean hasMissingTax = false;
        for (OrderFillExecution fillExecution : fillExecutions) {
            if (fillExecution.fillPrice() == null) {
                continue;
            }
            filledQuantity = filledQuantity.add(fillExecution.fillQuantity());
            grossNotional = grossNotional.add(
                    fillExecution.fillQuantity().multiply(fillExecution.fillPrice())
            );
            if (fillExecution.feeAmount() == null) {
                hasMissingFee = true;
                continue;
            }
            feeAmount = feeAmount.add(fillExecution.feeAmount());
            if (fillExecution.taxAmount() == null) {
                hasMissingTax = true;
                continue;
            }
            taxAmount = taxAmount.add(fillExecution.taxAmount());
        }

        // 평균 체결가는 모든 체결 수량에 가격이 있을 때만 계산한다.
        if (filledQuantity.compareTo(order.filledQuantity()) != 0) {
            return FillSummary.empty();
        }

        return new FillSummary(
                grossNotional.divide(filledQuantity, 10, RoundingMode.HALF_UP),
                grossNotional,
                hasMissingFee ? null : feeAmount,
                hasMissingTax ? null : taxAmount
        );
    }

    private record FillSummary(
            BigDecimal averageFillPrice,
            BigDecimal grossNotional,
            BigDecimal feeAmount,
            BigDecimal taxAmount
    ) {

        private static FillSummary empty() {
            return new FillSummary(null, null, null, null);
        }
    }
}
