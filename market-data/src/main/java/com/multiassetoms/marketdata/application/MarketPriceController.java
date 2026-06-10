package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.model.MarketPrice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data/instruments")
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    public MarketPriceController(MarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    @PutMapping("/{instrumentId}/prices/latest")
    public MarketPrice upsertLatestPrice(
            @PathVariable("instrumentId") String instrumentId,
            @RequestBody MarketPriceUpsertRequest request
    ) {
        return marketPriceService.upsertLatestPrice(
                instrumentId,
                request.requirePrice(),
                request.observedAt()
        );
    }

    @GetMapping("/{instrumentId}/prices/latest")
    public MarketPrice latestPrice(@PathVariable("instrumentId") String instrumentId) {
        return marketPriceService.latestPrice(instrumentId);
    }
}
