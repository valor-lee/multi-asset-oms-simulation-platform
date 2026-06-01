package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayMismatchReason;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 현재 order snapshot과 replay 결과를 비교하는 consistency 계산 서비스다.
 *
 * <p>Order 조회와 replay 입력 조립은 query-facing service가 맡고, 이 service는 이미 준비된
 * 실제 order와 replay 결과를 비교한다. 이렇게 두면 저장소 조회 없이도 mismatch 판단 규칙을
 * 독립적으로 테스트하고 확장할 수 있다.</p>
 */
@Service
public class OrderReplayConsistencyService {

    private final Clock clock;

    public OrderReplayConsistencyService(Clock clock) {
        this.clock = clock;
    }

    /**
     * 현재 order snapshot과 이벤트 기반 replay 결과가 일치하는지 검증한다.
     *
     * @param order 실제 order snapshot
     * @param replayResult 이벤트 기반 replay 결과
     * @return 실제 상태/수량과 replay 상태/수량 비교 결과
     */
    public OrderReplayConsistencyResult check(Order order, OrderReplayResult replayResult) {
        if (order == null) {
            throw new OrderReplayException("order is required");
        }
        if (replayResult == null) {
            throw new OrderReplayException("replayResult is required");
        }
        if (!order.orderId().equals(replayResult.orderId())) {
            throw new OrderReplayException("orderId does not match replayResult");
        }

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
