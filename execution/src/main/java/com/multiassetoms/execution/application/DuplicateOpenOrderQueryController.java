package com.multiassetoms.execution.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class DuplicateOpenOrderQueryController {

    private final DuplicateOpenOrderQueryService duplicateOpenOrderQueryService;

    public DuplicateOpenOrderQueryController(DuplicateOpenOrderQueryService duplicateOpenOrderQueryService) {
        this.duplicateOpenOrderQueryService = duplicateOpenOrderQueryService;
    }

    @GetMapping("/duplicate-open-order")
    public DuplicateOpenOrderResult findDuplicateOpenOrder(
            @RequestParam("portfolioId") String portfolioId,
            @RequestParam("instrumentId") String instrumentId,
            @RequestParam("side") OrderSide side,
            @RequestParam("orderType") OrderType orderType,
            @RequestParam("quantity") BigDecimal quantity,
            @RequestParam(value = "limitPrice", required = false) BigDecimal limitPrice,
            @RequestParam("timeInForce") TimeInForce timeInForce,
            @RequestParam(value = "excludeIntentId", required = false) UUID excludeIntentId
    ) {
        return duplicateOpenOrderQueryService.findDuplicateOpenOrder(
                portfolioId,
                instrumentId,
                side,
                orderType,
                quantity,
                limitPrice,
                timeInForce,
                excludeIntentId
        );
    }
}
