package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.model.PostTradeRequestException;

import java.time.LocalDate;

public record SettlementScheduleRequest(LocalDate settlementDate) {

    public LocalDate requireSettlementDate() {
        if (settlementDate == null) {
            throw new PostTradeRequestException("settlementDate is required");
        }
        return settlementDate;
    }
}
