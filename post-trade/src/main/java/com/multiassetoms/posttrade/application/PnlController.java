package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import com.multiassetoms.posttrade.model.UnrealizedPnlSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade")
public class PnlController {

    private final RealizedPnlService realizedPnlService;
    private final UnrealizedPnlService unrealizedPnlService;

    public PnlController(
            RealizedPnlService realizedPnlService,
            UnrealizedPnlService unrealizedPnlService
    ) {
        this.realizedPnlService = realizedPnlService;
        this.unrealizedPnlService = unrealizedPnlService;
    }

    @PostMapping("/trades/{tradeId}/realized-pnl-postings")
    public RealizedPnlEntry postRealizedPnl(
            @PathVariable("tradeId") UUID tradeId,
            @RequestBody RealizedPnlPostRequest request
    ) {
        return realizedPnlService.post(tradeId, request.requireAverageCost());
    }

    @GetMapping("/portfolios/{portfolioId}/realized-pnl")
    public CurrentRealizedPnlResponse currentRealizedPnl(
            @PathVariable("portfolioId") String portfolioId
    ) {
        return new CurrentRealizedPnlResponse(
                portfolioId,
                realizedPnlService.currentRealizedPnl(portfolioId)
        );
    }

    @GetMapping("/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl")
    public UnrealizedPnlSnapshot unrealizedPnlSnapshot(
            @PathVariable("portfolioId") String portfolioId,
            @PathVariable("instrumentId") String instrumentId,
            @RequestParam("averageCost") BigDecimal averageCost,
            @RequestParam("marketPrice") BigDecimal marketPrice
    ) {
        return unrealizedPnlService.snapshot(portfolioId, instrumentId, averageCost, marketPrice);
    }

    @GetMapping("/portfolios/{portfolioId}/positions/{instrumentId}/unrealized-pnl/latest")
    public UnrealizedPnlSnapshot unrealizedPnlSnapshotWithLatestMarketPrice(
            @PathVariable("portfolioId") String portfolioId,
            @PathVariable("instrumentId") String instrumentId,
            @RequestParam("averageCost") BigDecimal averageCost
    ) {
        return unrealizedPnlService.snapshotWithLatestMarketPrice(
                portfolioId,
                instrumentId,
                averageCost
        );
    }
}
