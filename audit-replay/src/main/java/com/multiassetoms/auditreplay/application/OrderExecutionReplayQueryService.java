package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 저장된 order row를 조회해 replay 입력을 준비하는 query-facing service다.
 *
 * {@link OrderExecutionReplayService}는 order quantity를 이미 알고 있다는 전제에서
 * audit event를 적용하는 replay engine 역할만 맡긴다.
 * Repository 조회까지 그 안에 넣으면 "주문 조회"와 "이벤트 기반 상태 재현" 책임이 섞이므로,
 * 운영 조회 API에서 필요한 입력 조립은 이 service가 담당한다.
 */
@Service
public class OrderExecutionReplayQueryService {

    private final OrderRepository orderRepository;
    private final OrderExecutionReplayService replayService;

    public OrderExecutionReplayQueryService(
            OrderRepository orderRepository,
            OrderExecutionReplayService replayService
    ) {
        this.orderRepository = orderRepository;
        this.replayService = replayService;
    }

    /**
     * 저장된 order row의 원 주문 수량을 사용해 execution replay를 수행한다.
     *
     * @param orderId replay할 order id
     * @return 이벤트 기반 replay 결과
     */
    public OrderReplayResult replayStoredOrder(UUID orderId) {
        if (orderId == null) {
            throw new OrderReplayException("orderId is required");
        }

        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderReplayException("order not found"));
        return replayService.replay(order.orderId(), order.quantity());
    }
}
