package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderSubmissionController {

    private final OrderSubmissionService orderSubmissionService;

    public OrderSubmissionController(OrderSubmissionService orderSubmissionService) {
        this.orderSubmissionService = orderSubmissionService;
    }

    @PostMapping("/{orderId}/submissions")
    public Order submit(@PathVariable("orderId") UUID orderId) {
        return orderSubmissionService.submit(orderId);
    }
}
