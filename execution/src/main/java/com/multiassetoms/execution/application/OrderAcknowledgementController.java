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
public class OrderAcknowledgementController {

    private final OrderAcknowledgementService orderAcknowledgementService;

    public OrderAcknowledgementController(OrderAcknowledgementService orderAcknowledgementService) {
        this.orderAcknowledgementService = orderAcknowledgementService;
    }

    @PostMapping("/{orderId}/acknowledgements")
    public Order acknowledge(
            @PathVariable("orderId") UUID orderId,
            @RequestBody OrderExecutionEventRequest request
    ) {
        return orderAcknowledgementService.acknowledge(orderId, request.requireEventId());
    }

    @PostMapping("/{orderId}/rejections")
    public Order reject(
            @PathVariable("orderId") UUID orderId,
            @RequestBody OrderExecutionEventRequest request
    ) {
        return orderAcknowledgementService.reject(orderId, request.requireEventId());
    }
}
