package com.multiassetoms.posttrade.model;

public record LedgerPostingResult(
        PositionLedgerEntry positionLedgerEntry,
        CashLedgerEntry cashLedgerEntry
) {
}
