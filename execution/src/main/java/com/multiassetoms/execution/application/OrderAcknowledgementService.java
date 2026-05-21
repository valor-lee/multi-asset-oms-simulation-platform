package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.OrderExecutionEventRepository;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderAcknowledgementException;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderAcknowledgementService {

    private final OrderRepository orderRepository;
    private final OrderExecutionEventRepository eventRepository;
    private final Clock clock;

    public OrderAcknowledgementService(
            OrderRepository orderRepository,
            OrderExecutionEventRepository eventRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * broker/exchange가 주문을 접수했음을 반영한다.
     * 상태별 처리:
     * - SENT -> ACKED로 전이해 저장
     * - ACKED: 중복 ack로 보고 기존 order 반환
     * - 그 외 상태: ack 대상이 아니므로 예외
     *
     * @param orderId ack 처리할 order id
     * @return ACKED 상태의 order
     */
    public Order acknowledge(UUID orderId) {
        return acknowledge(orderId, UUID.randomUUID());
    }

    /**
     * broker/exchange ack 이벤트를 idempotent하게 반영한다.
     * 상태별 처리:
     * - SENT -> ACKED로 전이해 저장
     * - ACKED: 중복 ack로 보고 기존 order 반환
     * - 이미 처리한 eventId: 중복 이벤트로 보고 현재 order 반환
     * - 그 외 상태: ack 대상이 아니므로 예외
     *
     * @param orderId ack 처리할 order id
     * @param eventId broker/exchange ack 이벤트 id
     * @return ACKED 또는 현재 최신 상태의 order
     */
    public Order acknowledge(UUID orderId, UUID eventId) {
        validateEventId(eventId);

        OrderExecutionEvent existingEvent = eventRepository.findByEventId(eventId)
                .orElse(null);

        if (existingEvent != null) {
            return duplicateEventResult(orderId, existingEvent, OrderExecutionEventType.ACKNOWLEDGED);
        }

        Order order = findOrder(orderId);

        if (order.status() == OrderStatus.ACKED) {
            return order;
        }

        validateSent(order);

        return transition(order, eventId, OrderStatus.ACKED, OrderExecutionEventType.ACKNOWLEDGED);
    }

    /**
     * broker/exchange가 주문을 거절했음을 반영한다.
     * 상태별 처리:
     * - SENT -> REJECTED로 전이해 저장
     * - REJECTED: 중복 reject로 보고 기존 order 반환
     * - 그 외 상태: reject 대상이 아니므로 예외
     *
     * @param orderId reject 처리할 order id
     * @return REJECTED 상태의 order
     */
    public Order reject(UUID orderId) {
        return reject(orderId, UUID.randomUUID());
    }

    /**
     * broker/exchange reject 이벤트를 idempotent하게 반영한다.
     * 상태별 처리:
     * - SENT -> REJECTED로 전이해 저장
     * - REJECTED: 중복 reject로 보고 기존 order 반환
     * - 이미 처리한 eventId: 중복 이벤트로 보고 현재 order 반환
     * - 그 외 상태: reject 대상이 아니므로 예외
     *
     * @param orderId reject 처리할 order id
     * @param eventId broker/exchange reject 이벤트 id
     * @return REJECTED 또는 현재 최신 상태의 order
     */
    public Order reject(UUID orderId, UUID eventId) {
        validateEventId(eventId);

        OrderExecutionEvent existingEvent = eventRepository.findByEventId(eventId)
                .orElse(null);

        if (existingEvent != null) {
            return duplicateEventResult(orderId, existingEvent, OrderExecutionEventType.REJECTED);
        }

        Order order = findOrder(orderId);

        if (order.status() == OrderStatus.REJECTED) {
            return order;
        }

        validateSent(order);

        return transition(order, eventId, OrderStatus.REJECTED, OrderExecutionEventType.REJECTED);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderAcknowledgementException("order not found"));
    }

    private void validateSent(Order order) {
        if (order.status() != OrderStatus.SENT) {
            throw new OrderAcknowledgementException(
                    "only SENT orders can be acknowledged or rejected"
            );
        }
    }

    private void validateEventId(UUID eventId) {
        if (eventId == null) {
            throw new OrderAcknowledgementException("eventId is required");
        }
    }

    private Order duplicateEventResult(
            UUID orderId,
            OrderExecutionEvent existingEvent,
            OrderExecutionEventType expectedType
    ) {
        if (!existingEvent.orderId().equals(orderId)) {
            throw new OrderAcknowledgementException("eventId belongs to another order");
        }
        if (existingEvent.eventType() != expectedType) {
            throw new OrderAcknowledgementException("eventId belongs to another execution event type");
        }
        return findOrder(orderId);
    }

    private Order transition(
            Order order,
            UUID eventId,
            OrderStatus nextStatus,
            OrderExecutionEventType eventType
    ) {
        Instant transitionedAt = Instant.now(clock);

        Order transitionedOrder = orderRepository.save(new Order(
                order.orderId(),
                order.intentId(),
                order.portfolioId(),
                order.instrumentId(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.filledQuantity(),
                order.limitPrice(),
                order.timeInForce(),
                nextStatus,
                order.createdAt(),
                transitionedAt
        ));
        eventRepository.save(new OrderExecutionEvent(
                eventId,
                order.orderId(),
                eventType,
                transitionedAt
        ));
        return transitionedOrder;
    }
}
