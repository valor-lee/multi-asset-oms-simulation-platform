package com.multiassetoms.posttrade.application;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/post-trade/portfolios")
public class LedgerBalanceController {

    private final PositionLedgerService positionLedgerService;
    private final CashLedgerService cashLedgerService;

    public LedgerBalanceController(
            PositionLedgerService positionLedgerService,
            CashLedgerService cashLedgerService
    ) {
        this.positionLedgerService = positionLedgerService;
        this.cashLedgerService = cashLedgerService;
    }

    @GetMapping("/{portfolioId}/positions/{instrumentId}")
    public CurrentPositionResponse currentPosition(
            @PathVariable("portfolioId") String portfolioId,
            @PathVariable("instrumentId") String instrumentId
    ) {
        return new CurrentPositionResponse(
                portfolioId,
                instrumentId,
                positionLedgerService.currentPosition(portfolioId, instrumentId)
        );
    }

    @GetMapping("/{portfolioId}/cash")
    public CurrentCashResponse currentCash(@PathVariable("portfolioId") String portfolioId) {
        return new CurrentCashResponse(
                portfolioId,
                cashLedgerService.currentCash(portfolioId)
        );
    }
}
