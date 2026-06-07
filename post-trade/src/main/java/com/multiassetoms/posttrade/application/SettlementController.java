package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.Settlement;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/trades/{tradeId}/settlements")
    public Settlement scheduleSettlement(
            @PathVariable("tradeId") UUID tradeId,
            @RequestBody SettlementScheduleRequest request
    ) {
        return settlementService.scheduleSettlement(tradeId, request.requireSettlementDate());
    }

    @PostMapping("/settlements/{settlementId}/confirmations")
    public Settlement confirmSettlement(@PathVariable("settlementId") UUID settlementId) {
        return settlementService.confirmSettlement(settlementId);
    }
}
