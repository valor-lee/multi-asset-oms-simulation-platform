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
public class OrderFillController {

    private final OrderFillService orderFillService;

    public OrderFillController(OrderFillService orderFillService) {
        this.orderFillService = orderFillService;
    }

    @PostMapping("/{orderId}/fills")
    public Order fill(
            @PathVariable("orderId") UUID orderId,
            @RequestBody OrderFillRequest request
    ) {
        return orderFillService.fill(
                orderId,
                request.requireFillExecutionId(),
                request.requireFillQuantity(),
                request.validatedFillPrice(),
                request.validatedFeeAmount(),
                request.validatedTaxAmount()
        );
    }
}
