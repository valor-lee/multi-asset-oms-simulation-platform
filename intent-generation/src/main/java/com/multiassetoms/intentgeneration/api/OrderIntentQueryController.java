package com.multiassetoms.intentgeneration.api;

import com.multiassetoms.intentgeneration.application.OrderIntentQueryService;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/order-intents")
public class OrderIntentQueryController {

    private final OrderIntentQueryService orderIntentQueryService;

    public OrderIntentQueryController(OrderIntentQueryService orderIntentQueryService) {
        this.orderIntentQueryService = orderIntentQueryService;
    }

    @GetMapping("/{intentId}")
    public OrderIntent getByIntentId(@PathVariable("intentId") UUID intentId) {
        return orderIntentQueryService.getByIntentId(intentId);
    }

    @GetMapping(params = "idempotencyKey")
    public OrderIntent getByIdempotencyKey(@RequestParam("idempotencyKey") String idempotencyKey) {
        return orderIntentQueryService.getByIdempotencyKey(idempotencyKey);
    }
}
