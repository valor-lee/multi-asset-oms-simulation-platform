package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionSimulationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class ExecutionSimulationController {

    private final ExecutionSimulationService executionSimulationService;

    public ExecutionSimulationController(ExecutionSimulationService executionSimulationService) {
        this.executionSimulationService = executionSimulationService;
    }

    @PostMapping("/{orderId}/execution-simulations")
    public ExecutionSimulationResult simulate(
            @PathVariable("orderId") UUID orderId,
            @RequestBody ExecutionSimulationRequest request
    ) {
        return executionSimulationService.simulate(
                orderId,
                request.requireSimulationId(),
                request.requireFillQuantity(),
                request.requireSlippageRate(),
                request.rejectRateOrZero()
        );
    }
}
