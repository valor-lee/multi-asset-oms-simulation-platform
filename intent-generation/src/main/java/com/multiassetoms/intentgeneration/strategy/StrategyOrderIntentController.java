package com.multiassetoms.intentgeneration.strategy;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-intents/strategy")
public class StrategyOrderIntentController {

    private final StrategyOrderIntentService strategyOrderIntentService;

    public StrategyOrderIntentController(StrategyOrderIntentService strategyOrderIntentService) {
        this.strategyOrderIntentService = strategyOrderIntentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderIntent create(@RequestBody StrategyOrderIntentRequest request) {
        return strategyOrderIntentService.create(request);
    }
}
