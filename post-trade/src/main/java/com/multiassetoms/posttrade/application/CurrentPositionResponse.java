package com.multiassetoms.posttrade.application;

import java.math.BigDecimal;

public record CurrentPositionResponse(
        String portfolioId,
        String instrumentId,
        BigDecimal quantity
) {
}
