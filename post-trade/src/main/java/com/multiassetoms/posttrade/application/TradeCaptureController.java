package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.Trade;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/post-trade/orders")
public class TradeCaptureController {

    private final TradeCaptureService tradeCaptureService;

    public TradeCaptureController(TradeCaptureService tradeCaptureService) {
        this.tradeCaptureService = tradeCaptureService;
    }

    @PostMapping("/{orderId}/trades")
    public Trade capture(@PathVariable("orderId") UUID orderId) {
        return tradeCaptureService.capture(orderId);
    }
}
