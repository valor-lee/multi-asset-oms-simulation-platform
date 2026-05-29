package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderAuditEvent;
import com.multiassetoms.auditreplay.model.OrderAuditEventSource;
import com.multiassetoms.auditreplay.model.OrderAuditTrail;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderExecutionReplayService {

    private static final OrderStatus INITIAL_STATUS = OrderStatus.SENT;

    private final OrderAuditTrailService auditTrailService;
    private final Clock clock;

    public OrderExecutionReplayService(OrderAuditTrailService auditTrailService, Clock clock) {
        this.auditTrailService = auditTrailService;
        this.clock = clock;
    }

    /**
     * audit trail 이벤트를 순서대로 적용해 SENT 이후 주문 실행 결과를 재현한다.
     *
     * @param orderId 재현할 order id
     * @param orderQuantity 원 주문 수량
     * @return 재현된 주문 상태와 누적 체결 수량
     */
    public OrderReplayResult replay(UUID orderId, BigDecimal orderQuantity) {
        validateInputs(orderId, orderQuantity);

        OrderAuditTrail auditTrail = auditTrailService.auditTrail(orderId);
        ReplayState state = new ReplayState(INITIAL_STATUS, BigDecimal.ZERO);
        for (OrderAuditEvent event : auditTrail.events()) {
            state = apply(event, state, orderQuantity);
        }

        return new OrderReplayResult(
                orderId,
                INITIAL_STATUS,
                state.status(),
                orderQuantity,
                state.filledQuantity(),
                auditTrail.events().size(),
                Instant.now(clock)
        );
    }

    private void validateInputs(UUID orderId, BigDecimal orderQuantity) {
        if (orderId == null) {
            throw new OrderReplayException("orderId is required");
        }
        if (orderQuantity == null || orderQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderReplayException("orderQuantity must be greater than zero");
        }
    }

    private ReplayState apply(
            OrderAuditEvent event,
            ReplayState state,
            BigDecimal orderQuantity
    ) {
        if (event.source() == OrderAuditEventSource.FILL_EXECUTION) {
            return applyFill(event, state, orderQuantity);
        }
        return applyExecutionEvent(event, state);
    }

    private ReplayState applyExecutionEvent(OrderAuditEvent event, ReplayState state) {
        return switch (event.eventType()) {
            case "ACKNOWLEDGED" -> new ReplayState(OrderStatus.ACKED, state.filledQuantity());
            case "REJECTED" -> new ReplayState(OrderStatus.REJECTED, state.filledQuantity());
            case "CANCEL_CONFIRMED" -> new ReplayState(OrderStatus.CANCELED, state.filledQuantity());
            default -> throw new OrderReplayException("unsupported execution event type: " + event.eventType());
        };
    }

    private ReplayState applyFill(
            OrderAuditEvent event,
            ReplayState state,
            BigDecimal orderQuantity
    ) {
        if (event.fillQuantity() == null || event.fillQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderReplayException("fillQuantity must be greater than zero");
        }

        BigDecimal nextFilledQuantity = state.filledQuantity().add(event.fillQuantity());
        if (nextFilledQuantity.compareTo(orderQuantity) > 0) {
            throw new OrderReplayException("replayed filled quantity exceeds order quantity");
        }

        OrderStatus nextStatus = nextFilledQuantity.compareTo(orderQuantity) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
        return new ReplayState(nextStatus, nextFilledQuantity);
    }

    private record ReplayState(
            OrderStatus status,
            BigDecimal filledQuantity
    ) {
    }
}
