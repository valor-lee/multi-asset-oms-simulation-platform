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
    private final OrderReplayConsistencyQueryService consistencyQueryService;
    private final Clock clock;

    public OrderReplayConsistencyReportService(
            OrderRepository orderRepository,
            OrderReplayConsistencyQueryService consistencyQueryService,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.consistencyQueryService = consistencyQueryService;
        this.clock = clock;
    }

    /**
     * 현재 저장된 모든 order row를 replay 결과와 대조해 consistency report를 만든다.
     *
     * @return 전체 주문 consistency 집계와 개별 주문 검증 결과
     */
    public OrderReplayConsistencyReport checkAll() {
        List<OrderReplayConsistencyResult> results = checkOrders();
        return toReport(results, results);
    }

    /**
     * 현재 저장된 모든 order row를 점검하되, report 결과 목록에는 불일치 주문만 담는다.
     *
     * @return 전체 주문 consistency 집계와 불일치 주문 검증 결과
     */
    public OrderReplayConsistencyReport checkInconsistentOnly() {
        List<OrderReplayConsistencyResult> results = checkOrders();
        List<OrderReplayConsistencyResult> inconsistentResults = results.stream()
                .filter(result -> !result.consistent())
                .toList();

        return toReport(results, inconsistentResults);
    }

    private List<OrderReplayConsistencyResult> checkOrders() {
        return orderRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(Order::createdAt)
                        .thenComparing(Order::orderId))
                .map(order -> consistencyQueryService.checkStoredOrder(order.orderId()))
                .toList();
    }

    private OrderReplayConsistencyReport toReport(
            List<OrderReplayConsistencyResult> allResults,
            List<OrderReplayConsistencyResult> reportResults
    ) {
        int consistentCount = (int) allResults.stream()
                .filter(OrderReplayConsistencyResult::consistent)
                .count();

        return new OrderReplayConsistencyReport(
                allResults.size(),
                consistentCount,
                allResults.size() - consistentCount,
                reportResults,
                Instant.now(clock)
        );
    }
}
