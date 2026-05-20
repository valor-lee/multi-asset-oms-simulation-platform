package com.multiassetoms.execution.application;

import com.multiassetoms.execution.application.port.OrderExecutionEventRepository;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderCancellationException;
import com.multiassetoms.execution.model.OrderExecutionEvent;
import com.multiassetoms.execution.model.OrderExecutionEventType;
import com.multiassetoms.execution.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderExecutionEventRepository eventRepository;
    private final Clock clock;

    public OrderCancellationService(
            OrderRepository orderRepository,
            OrderExecutionEventRepository eventRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * broker/exchange에 취소 요청을 보냈음을 내부 상태에 반영한다.
     * 상태별 처리:
     * - ACKED: CANCEL_REQUESTED로 전이
     * - PARTIALLY_FILLED: 남은 수량 취소 요청으로 보고 CANCEL_REQUESTED로 전이
     * - CANCEL_REQUESTED: 중복 요청으로 보고 기존 order 반환
     * - 그 외 상태: 취소 요청 대상이 아니므로 예외
     *
     * @param orderId 취소 요청할 order id
     * @return CANCEL_REQUESTED 상태의 order
     */
    public Order requestCancel(UUID orderId) {
        Order order = findOrder(orderId);
        if (order.status() == OrderStatus.CANCEL_REQUESTED) {
            return order;
        }
        validateCancelable(order);
        return transitionOrderOnly(order, OrderStatus.CANCEL_REQUESTED);
    }

    /**
     * broker/exchange가 취소 완료를 응답했음을 내부 상태에 반영한다.
     * 상태별 처리:
     * - CANCEL_REQUESTED: CANCELED로 전이
     * - CANCELED: 중복 응답으로 보고 기존 order 반환
     * - 그 외 상태: 취소 완료 대상이 아니므로 예외
     *
     * @param orderId 취소 완료 처리할 order id
     * @return CANCELED 상태의 order
     */
    public Order confirmCancel(UUID orderId) {
        return confirmCancel(orderId, UUID.randomUUID());
    }

    /**
     * broker/exchange cancel confirmation 이벤트를 idempotent하게 반영한다.
     * 상태별 처리:
     * - CANCEL_REQUESTED: CANCELED로 전이
     * - CANCELED: 중복 응답으로 보고 기존 order 반환
     * - 이미 처리한 eventId: 중복 이벤트로 보고 현재 order 반환
     * - 그 외 상태: 취소 완료 대상이 아니므로 예외
     *
     * @param orderId 취소 완료 처리할 order id
     * @param eventId broker/exchange cancel confirmation 이벤트 id
     * @return CANCELED 또는 현재 최신 상태의 order
     */
    public Order confirmCancel(UUID orderId, UUID eventId) {
        validateEventId(eventId);
        OrderExecutionEvent existingEvent = eventRepository.findByEventId(eventId)
                .orElse(null);

        if (existingEvent != null) {
            return duplicateEventResult(orderId, existingEvent);
        }

        Order order = findOrder(orderId);
        if (order.status() == OrderStatus.CANCELED) {
            return order;
        }
        validateCancelRequested(order);
        return transitionOrderWithExecutionEvent(
                order,
                eventId,
                OrderStatus.CANCELED,
                OrderExecutionEventType.CANCEL_CONFIRMED
        );
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderCancellationException("order not found"));
    }

    private void validateCancelable(Order order) {
        if (order.status() != OrderStatus.ACKED
                && order.status() != OrderStatus.PARTIALLY_FILLED) {
            throw new OrderCancellationException(
                    "only ACKED or PARTIALLY_FILLED orders can be canceled"
            );
        }
    }

    private void validateCancelRequested(Order order) {
        if (order.status() != OrderStatus.CANCEL_REQUESTED) {
            throw new OrderCancellationException(
                    "only CANCEL_REQUESTED orders can be confirmed canceled"
            );
        }
    }

    private void validateEventId(UUID eventId) {
        if (eventId == null) {
            throw new OrderCancellationException("eventId is required");
        }
    }

    private Order duplicateEventResult(UUID orderId, OrderExecutionEvent existingEvent) {
        if (!existingEvent.orderId().equals(orderId)) {
            throw new OrderCancellationException("eventId belongs to another order");
        }
        if (existingEvent.eventType() != OrderExecutionEventType.CANCEL_CONFIRMED) {
            throw new OrderCancellationException("eventId belongs to another execution event type");
        }
        return findOrder(orderId);
    }

    private Order transitionOrderOnly(Order order, OrderStatus nextStatus) {
        Instant transitionedAt = Instant.now(clock);
        return orderRepository.save(new Order(
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
    }

    private Order transitionOrderWithExecutionEvent(
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
