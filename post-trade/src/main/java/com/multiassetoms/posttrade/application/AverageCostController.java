package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.AverageCostSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade")
public class AverageCostController {

    private final AverageCostService averageCostService;

    public AverageCostController(AverageCostService averageCostService) {
        this.averageCostService = averageCostService;
    }

    @PostMapping("/trades/{tradeId}/average-cost-postings")
    public AverageCostEntry post(@PathVariable("tradeId") UUID tradeId) {
        return averageCostService.post(tradeId);
    }

    @GetMapping("/portfolios/{portfolioId}/positions/{instrumentId}/average-cost")
    public AverageCostSnapshot currentAverageCost(
            @PathVariable("portfolioId") String portfolioId,
            @PathVariable("instrumentId") String instrumentId
    ) {
        return averageCostService.currentAverageCost(portfolioId, instrumentId);
    }
}
