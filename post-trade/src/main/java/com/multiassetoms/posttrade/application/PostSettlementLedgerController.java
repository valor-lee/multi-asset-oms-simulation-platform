package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.LedgerPostingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade/trades")
public class PostSettlementLedgerController {

    private final PostSettlementLedgerService postSettlementLedgerService;

    public PostSettlementLedgerController(PostSettlementLedgerService postSettlementLedgerService) {
        this.postSettlementLedgerService = postSettlementLedgerService;
    }

    @PostMapping("/{tradeId}/ledger-postings")
    public LedgerPostingResult post(@PathVariable("tradeId") UUID tradeId) {
        return postSettlementLedgerService.post(tradeId);
    }
}
