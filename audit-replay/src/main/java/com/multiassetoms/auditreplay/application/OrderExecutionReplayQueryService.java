package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayException;
import com.multiassetoms.auditreplay.model.OrderReplayResult;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
