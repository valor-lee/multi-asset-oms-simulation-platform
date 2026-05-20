package com.multiassetoms.execution.model;

public enum OrderStatus {
    CREATED,
    SENT,
    ACKED,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_REQUESTED,
    CANCELED,
    REJECTED
}
