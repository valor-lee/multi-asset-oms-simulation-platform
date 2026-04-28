package com.multiassetoms.intentgeneration.manual;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-intents/manual")
public class ManualOrderIntentController {

    private final ManualOrderIntentService manualOrderIntentService;

    public ManualOrderIntentController(ManualOrderIntentService manualOrderIntentService) {
        this.manualOrderIntentService = manualOrderIntentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderIntent create(@RequestBody ManualOrderIntentRequest request) {
        return manualOrderIntentService.create(request);
    }
}
