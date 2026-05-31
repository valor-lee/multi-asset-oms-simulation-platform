package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderAuditTrail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-replay/order-audit-trails")
public class OrderAuditTrailController {

    private final OrderAuditTrailService auditTrailService;

    public OrderAuditTrailController(OrderAuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @GetMapping("/{orderId}")
    public OrderAuditTrail auditTrail(@PathVariable("orderId") UUID orderId) {
        return auditTrailService.auditTrail(orderId);
    }
}
