package com.multiassetoms.pretraderisk.application;

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

    private final PreTradeRiskOrderIntentService preTradeRiskOrderIntentService;

    public PreTradeRiskOrderIntentController(PreTradeRiskOrderIntentService preTradeRiskOrderIntentService) {
        this.preTradeRiskOrderIntentService = preTradeRiskOrderIntentService;
    }

    @PostMapping("/{intentId}/evaluations")
    public PreTradeRiskOrderIntentResult evaluate(
            @PathVariable("intentId") UUID intentId,
            @RequestBody(required = false) PreTradeRiskEvaluationRequest request
    ) {
        PreTradeRiskCheckContext checkContext = request == null
                ? PreTradeRiskCheckContext.empty()
                : request.toCheckContext();

        return preTradeRiskOrderIntentService.evaluate(intentId, checkContext);
    }

    @PostMapping("/{intentId}/evaluations/latest-price-band")
    public PreTradeRiskOrderIntentResult evaluateWithLatestPriceBand(
            @PathVariable("intentId") UUID intentId,
            @RequestBody PreTradeRiskLatestPriceBandEvaluationRequest request
    ) {
        return preTradeRiskOrderIntentService.evaluateWithLatestPriceBand(
                intentId,
                request.toBaseCheckContext(),
                request.requirePriceBandRate()
        );
    }

    @PostMapping("/{intentId}/evaluations/latest-price-band/duplicate-open-order")
    public PreTradeRiskOrderIntentResult evaluateWithLatestPriceBandAndDuplicateOpenOrder(
            @PathVariable("intentId") UUID intentId,
            @RequestBody PreTradeRiskLatestPriceBandDuplicateEvaluationRequest request
    ) {
        return preTradeRiskOrderIntentService.evaluateWithLatestPriceBandAndDuplicateOpenOrder(
                intentId,
                request.toBaseCheckContext(),
                request.requirePriceBandRate()
        );
    }
}
