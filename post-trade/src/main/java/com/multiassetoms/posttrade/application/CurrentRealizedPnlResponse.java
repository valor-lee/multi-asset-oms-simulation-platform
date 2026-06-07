package com.multiassetoms.posttrade.application;

import java.math.BigDecimal;

public record CurrentRealizedPnlResponse(
        String portfolioId,
        BigDecimal realizedPnl
) {
}
