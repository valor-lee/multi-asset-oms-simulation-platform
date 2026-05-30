package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayMismatchReason;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderReplayConsistencyService {

    private final OrderRepository orderRepository;
    private final OrderExecutionReplayService replayService;
    private final Clock clock;

    public OrderReplayConsistencyService(
            OrderRepository orderRepository,
            OrderExecutionReplayService replayService,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.replayService = replayService;
        this.clock = clock;
    }

    /**
     * 현재 저장된 order row와 이벤트 기반 replay 결과가 일치하는지 검증한다.
     *
     * @param orderId 검증할 order id
     * @return 실제 상태/수량과 replay 상태/수량 비교 결과
     */
    public OrderReplayConsistencyResult check(UUID orderId) {
        if (orderId == null) {
            throw new OrderReplayException("orderId is required");
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderReplayException("order not found"));
        OrderReplayResult replayResult = replayService.replay(order.orderId(), order.quantity());

        List<OrderReplayMismatchReason> mismatchReasons = mismatchReasons(order, replayResult);

        return new OrderReplayConsistencyResult(
                order.orderId(),
                mismatchReasons.isEmpty(),
                mismatchReasons,
                order.status(),
                replayResult.replayedStatus(),
                order.filledQuantity(),
                replayResult.replayedFilledQuantity(),
                replayResult.appliedEventCount(),
                Instant.now(clock)
        );
    }

    private List<OrderReplayMismatchReason> mismatchReasons(Order order, OrderReplayResult replayResult) {
        List<OrderReplayMismatchReason> reasons = new ArrayList<>();

        if (order.status() != replayResult.replayedStatus()) {
            reasons.add(OrderReplayMismatchReason.STATUS_MISMATCH);
        }
        if (order.filledQuantity().compareTo(replayResult.replayedFilledQuantity()) != 0) {
            reasons.add(OrderReplayMismatchReason.FILLED_QUANTITY_MISMATCH);
        }

        return List.copyOf(reasons);
    }
}
