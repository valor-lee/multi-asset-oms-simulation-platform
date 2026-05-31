package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayConsistencyReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-replay/order-replay/consistency-report")
public class OrderReplayConsistencyReportController {

    private final OrderReplayConsistencyReportService reportService;

    public OrderReplayConsistencyReportController(OrderReplayConsistencyReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public OrderReplayConsistencyReport report(
            @RequestParam(name = "inconsistentOnly", defaultValue = "false") boolean inconsistentOnly
    ) {
        if (inconsistentOnly) {
            return reportService.checkInconsistentOnly();
        }
        return reportService.checkAll();
    }
}
