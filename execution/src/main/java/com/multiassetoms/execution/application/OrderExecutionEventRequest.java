package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionRequestException;

import java.util.UUID;

public record OrderExecutionEventRequest(UUID eventId) {

    public UUID requireEventId() {
        if (eventId == null) {
            throw new ExecutionRequestException("eventId is required");
        }
        return eventId;
    }
}
