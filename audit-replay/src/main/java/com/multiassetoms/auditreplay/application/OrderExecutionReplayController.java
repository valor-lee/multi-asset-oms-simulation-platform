package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-replay/order-replay")
public class OrderExecutionReplayController {

    private final OrderExecutionReplayService replayService;

    public OrderExecutionReplayController(OrderExecutionReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/{orderId}")
    public OrderReplayResult replay(
            @PathVariable("orderId") UUID orderId,
            @RequestParam(name = "orderQuantity") BigDecimal orderQuantity
    ) {
        return replayService.replay(orderId, orderQuantity);
    }
}
