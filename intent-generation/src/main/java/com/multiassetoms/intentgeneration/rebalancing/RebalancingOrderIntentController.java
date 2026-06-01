package com.multiassetoms.intentgeneration.rebalancing;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-intents/rebalancing")
public class RebalancingOrderIntentController {

    private final RebalancingOrderIntentService rebalancingOrderIntentService;

    public RebalancingOrderIntentController(RebalancingOrderIntentService rebalancingOrderIntentService) {
        this.rebalancingOrderIntentService = rebalancingOrderIntentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderIntent create(@RequestBody RebalancingOrderIntentRequest request) {
        return rebalancingOrderIntentService.create(request);
    }
}
