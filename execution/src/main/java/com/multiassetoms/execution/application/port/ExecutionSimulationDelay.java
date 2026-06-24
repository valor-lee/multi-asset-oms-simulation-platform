package com.multiassetoms.execution.application.port;

@FunctionalInterface
public interface ExecutionSimulationDelay {

    long await();
}
