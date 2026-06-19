package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.application.OrderIntentQueryService;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOrderIntentResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pre-trade-risk/order-intents")
public class PreTradeRiskOrderIntentController {

    private final OrderIntentQueryService orderIntentQueryService;
    private final PreTradeRiskOrderIntentService preTradeRiskOrderIntentService;

    public PreTradeRiskOrderIntentController(
            OrderIntentQueryService orderIntentQueryService,
            PreTradeRiskOrderIntentService preTradeRiskOrderIntentService
    ) {
        this.orderIntentQueryService = orderIntentQueryService;
        this.preTradeRiskOrderIntentService = preTradeRiskOrderIntentService;
    }

    @PostMapping("/{intentId}/evaluations")
    public PreTradeRiskOrderIntentResult evaluate(
            @PathVariable("intentId") UUID intentId,
            @RequestBody(required = false) PreTradeRiskEvaluationRequest request
    ) {
        OrderIntent intent = orderIntentQueryService.getByIntentId(intentId);
        PreTradeRiskCheckContext checkContext = request == null
                ? PreTradeRiskCheckContext.empty()
                : request.toCheckContext();

        return preTradeRiskOrderIntentService.evaluate(intent, checkContext);
    }

    @PostMapping("/{intentId}/evaluations/latest-price-band")
    public PreTradeRiskOrderIntentResult evaluateWithLatestPriceBand(
            @PathVariable("intentId") UUID intentId,
            @RequestBody PreTradeRiskLatestPriceBandEvaluationRequest request
    ) {
        OrderIntent intent = orderIntentQueryService.getByIntentId(intentId);
        return preTradeRiskOrderIntentService.evaluateWithLatestPriceBand(
                intent,
                request.toBaseCheckContext(),
                request.requirePriceBandRate()
        );
    }
}
