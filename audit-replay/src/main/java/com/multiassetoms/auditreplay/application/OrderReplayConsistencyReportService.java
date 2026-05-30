package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyReport;
import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import com.multiassetoms.execution.application.port.OrderRepository;
import com.multiassetoms.execution.model.Order;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderReplayConsistencyReportService {

    private final OrderRepository orderRepository;
    private final OrderReplayConsistencyService consistencyService;
    private final Clock clock;

    public OrderReplayConsistencyReportService(
            OrderRepository orderRepository,
            OrderReplayConsistencyService consistencyService,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.consistencyService = consistencyService;
        this.clock = clock;
    }

    /**
     * 현재 저장된 모든 order row를 replay 결과와 대조해 consistency report를 만든다.
     *
     * @return 전체 주문 consistency 집계와 개별 주문 검증 결과
     */
    public OrderReplayConsistencyReport checkAll() {
        List<OrderReplayConsistencyResult> results = orderRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(Order::createdAt)
                        .thenComparing(Order::orderId))
                .map(order -> consistencyService.check(order.orderId()))
                .toList();

        int consistentCount = (int) results.stream()
                .filter(OrderReplayConsistencyResult::consistent)
                .count();

        return new OrderReplayConsistencyReport(
                results.size(),
                consistentCount,
                results.size() - consistentCount,
                results,
                Instant.now(clock)
        );
    }
}
