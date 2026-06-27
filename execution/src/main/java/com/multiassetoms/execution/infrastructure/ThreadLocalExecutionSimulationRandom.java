package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.ExecutionSimulationRandom;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadLocalExecutionSimulationRandom implements ExecutionSimulationRandom {

    @Override
    public BigDecimal nextUnitInterval() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble());
    }
}
