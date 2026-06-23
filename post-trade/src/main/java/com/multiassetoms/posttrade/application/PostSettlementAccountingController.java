package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.AccountingPostingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade/trades")
public class PostSettlementAccountingController {

    private final PostSettlementAccountingService postSettlementAccountingService;

    public PostSettlementAccountingController(PostSettlementAccountingService postSettlementAccountingService) {
        this.postSettlementAccountingService = postSettlementAccountingService;
    }

    @PostMapping("/{tradeId}/accounting-postings")
    public AccountingPostingResult post(@PathVariable("tradeId") UUID tradeId) {
        return postSettlementAccountingService.post(tradeId);
    }
}
