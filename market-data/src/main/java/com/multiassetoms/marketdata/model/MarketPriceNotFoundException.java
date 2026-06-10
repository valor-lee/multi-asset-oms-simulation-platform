package com.multiassetoms.marketdata.model;

public class MarketPriceNotFoundException extends RuntimeException {

    public MarketPriceNotFoundException(String message) {
        super(message);
    }
}
