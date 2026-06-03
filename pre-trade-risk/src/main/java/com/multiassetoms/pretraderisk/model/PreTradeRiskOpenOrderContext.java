package com.multiassetoms.pretraderisk.model;

import java.util.UUID;

public record PreTradeRiskOpenOrderContext(
        Boolean duplicateOpenOrderExists,
        UUID duplicateOpenOrderId
) {

    public PreTradeRiskOpenOrderContext(Boolean duplicateOpenOrderExists) {
        this(duplicateOpenOrderExists, null);
    }

    public static PreTradeRiskOpenOrderContext empty() {
        return new PreTradeRiskOpenOrderContext(null, null);
    }
}
