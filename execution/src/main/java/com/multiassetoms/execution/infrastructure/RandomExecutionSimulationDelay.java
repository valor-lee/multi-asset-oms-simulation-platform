package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.application.port.ExecutionSimulationDelay;
import com.multiassetoms.execution.model.ExecutionSimulationUnavailableException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomExecutionSimulationDelay implements ExecutionSimulationDelay {

    private static final long MIN_DELAY_MILLIS = 20L;
    private static final long MAX_DELAY_MILLIS = 200L;

    @Override
    public long await() {
        long delayMillis = ThreadLocalRandom.current()
                .nextLong(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS + 1);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExecutionSimulationUnavailableException(
                    "execution simulation delay was interrupted"
            );
        }
        return delayMillis;
    }
}
