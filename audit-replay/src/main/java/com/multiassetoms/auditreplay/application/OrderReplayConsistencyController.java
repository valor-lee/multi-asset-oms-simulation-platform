package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-replay/order-replay/consistency")
public class OrderReplayConsistencyController {

    private final OrderReplayConsistencyQueryService consistencyQueryService;

    public OrderReplayConsistencyController(OrderReplayConsistencyQueryService consistencyQueryService) {
        this.consistencyQueryService = consistencyQueryService;
    }

    @GetMapping("/{orderId}")
    public OrderReplayConsistencyResult check(@PathVariable("orderId") UUID orderId) {
        return consistencyQueryService.checkStoredOrder(orderId);
    }
}
