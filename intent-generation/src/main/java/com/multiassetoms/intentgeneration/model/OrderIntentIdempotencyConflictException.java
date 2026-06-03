package com.multiassetoms.intentgeneration.model;

public class OrderIntentIdempotencyConflictException extends RuntimeException {

    public OrderIntentIdempotencyConflictException(String message) {
        super(message);
    }
}
