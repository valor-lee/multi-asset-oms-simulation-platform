package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderCancellationController {

    private final OrderCancellationService orderCancellationService;

    public OrderCancellationController(OrderCancellationService orderCancellationService) {
        this.orderCancellationService = orderCancellationService;
    }

    @PostMapping("/{orderId}/cancel-requests")
    public Order requestCancel(@PathVariable("orderId") UUID orderId) {
        return orderCancellationService.requestCancel(orderId);
    }

    @PostMapping("/{orderId}/cancel-confirmations")
    public Order confirmCancel(
            @PathVariable("orderId") UUID orderId,
            @RequestBody OrderExecutionEventRequest request
    ) {
        return orderCancellationService.confirmCancel(orderId, request.requireEventId());
    }
}
