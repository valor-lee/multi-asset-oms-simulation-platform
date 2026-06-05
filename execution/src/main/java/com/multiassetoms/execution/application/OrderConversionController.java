package com.multiassetoms.execution.application;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/order-intents")
public class OrderConversionController {

    private final OrderConversionService orderConversionService;

    public OrderConversionController(OrderConversionService orderConversionService) {
        this.orderConversionService = orderConversionService;
    }

    @PostMapping("/{intentId}/orders")
    public OrderConversionResult convert(@PathVariable("intentId") UUID intentId) {
        return orderConversionService.convert(intentId);
    }
}
