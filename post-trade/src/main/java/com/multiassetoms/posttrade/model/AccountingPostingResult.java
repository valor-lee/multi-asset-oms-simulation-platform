package com.multiassetoms.posttrade.model;

public record AccountingPostingResult(
        PositionLedgerEntry positionLedgerEntry,
        CashLedgerEntry cashLedgerEntry,
        AverageCostEntry averageCostEntry,
        RealizedPnlEntry realizedPnlEntry
) {
}
