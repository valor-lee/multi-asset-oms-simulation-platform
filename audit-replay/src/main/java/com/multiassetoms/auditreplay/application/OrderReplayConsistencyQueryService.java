package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 저장된 order row를 조회해 consistency check 입력을 준비하는 query-facing service다.
 *
 * <p>{@link OrderReplayConsistencyService}는 이미 준비된 order snapshot과 replay 결과를 비교하는
 * 계산 책임만 맡긴다. Repository 조회와 replay 실행을 이 service에 두면 API/report 조회 흐름과
 * mismatch 판단 규칙을 분리할 수 있다.</p>
 */
@Service
public class OrderReplayConsistencyQueryService {

    private final OrderRepository orderRepository;
    private final OrderExecutionReplayService replayService;
    private final OrderReplayConsistencyService consistencyService;

    public OrderReplayConsistencyQueryService(
            OrderRepository orderRepository,
            OrderExecutionReplayService replayService,
            OrderReplayConsistencyService consistencyService
    ) {
        this.orderRepository = orderRepository;
        this.replayService = replayService;
        this.consistencyService = consistencyService;
    }

    /**
     * 저장된 order row와 이벤트 기반 replay 결과가 일치하는지 검증한다.
     *
     * @param orderId 검증할 order id
     * @return 실제 상태/수량과 replay 상태/수량 비교 결과
     */
    public OrderReplayConsistencyResult checkStoredOrder(UUID orderId) {
        if (orderId == null) {
            throw new OrderReplayException("orderId is required");
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderReplayException("order not found"));
        OrderReplayResult replayResult = replayService.replay(order.orderId(), order.quantity());
        return consistencyService.check(order, replayResult);
    }
}
