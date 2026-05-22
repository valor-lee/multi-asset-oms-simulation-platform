package com.multiassetoms.posttrade.model;

public record PositionKey(
        String portfolioId,
        String instrumentId
) {
}
