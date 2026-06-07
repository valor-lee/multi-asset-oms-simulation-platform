package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.PostTradeRequestException;

import java.math.BigDecimal;

public record RealizedPnlPostRequest(BigDecimal averageCost) {

    public BigDecimal requireAverageCost() {
        if (averageCost == null) {
            throw new PostTradeRequestException("averageCost is required");
        }
        return averageCost;
    }
}
