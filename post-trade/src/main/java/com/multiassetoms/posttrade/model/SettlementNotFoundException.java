package com.multiassetoms.posttrade.model;

public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(String message) {
        super(message);
    }
}
