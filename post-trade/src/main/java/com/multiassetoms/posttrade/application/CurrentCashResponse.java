package com.multiassetoms.posttrade.application;

import java.math.BigDecimal;

public record CurrentCashResponse(
        String portfolioId,
        BigDecimal cash
) {
}
