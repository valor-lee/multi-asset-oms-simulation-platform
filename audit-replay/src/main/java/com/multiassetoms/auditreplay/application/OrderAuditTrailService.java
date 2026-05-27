package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderAuditEvent;
import com.multiassetoms.auditreplay.model.OrderAuditEventType;
import com.multiassetoms.auditreplay.model.OrderAuditTrail;
import com.multiassetoms.execution.application.port.OrderExecutionEventRepository;
import com.multiassetoms.execution.application.port.OrderFillExecutionRepository;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderFillExecution;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class OrderAuditTrailService {

    private final OrderExecutionEventRepository executionEventRepository;
    private final OrderFillExecutionRepository fillExecutionRepository;

    public OrderAuditTrailService(
            OrderExecutionEventRepository executionEventRepository,
            OrderFillExecutionRepository fillExecutionRepository
    ) {
        this.executionEventRepository = executionEventRepository;
        this.fillExecutionRepository = fillExecutionRepository;
    }

    /**
     * order별 broker/exchange 이벤트와 fill 이벤트를 시간순 audit trail로 조회한다.
     *
     * @param orderId audit trail을 조회할 order id
     * @return 시간순으로 정렬된 order audit trail
     */
    public OrderAuditTrail auditTrail(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }

        List<OrderAuditEvent> events = Stream.concat(
                        executionEventRepository.findByOrderId(orderId).stream()
                                .map(this::toAuditEvent),
                        fillExecutionRepository.findByOrderId(orderId).stream()
                                .map(this::toAuditEvent)
                )
                .sorted(Comparator
                        .comparing(OrderAuditEvent::occurredAt)
                        .thenComparing(OrderAuditEvent::eventId))
                .toList();

        return new OrderAuditTrail(orderId, events);
    }

    private OrderAuditEvent toAuditEvent(OrderExecutionEvent event) {
        return new OrderAuditEvent(
                event.eventId(),
                event.orderId(),
                OrderAuditEventType.valueOf(event.eventType().name()),
                null,
                null,
                null,
                null,
                event.processedAt()
        );
    }

    private OrderAuditEvent toAuditEvent(OrderFillExecution fillExecution) {
        return new OrderAuditEvent(
                fillExecution.fillExecutionId(),
                fillExecution.orderId(),
                OrderAuditEventType.FILL,
                fillExecution.fillQuantity(),
                fillExecution.fillPrice(),
                fillExecution.feeAmount(),
                fillExecution.taxAmount(),
                fillExecution.processedAt()
        );
    }
}
