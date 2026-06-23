package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.AccountingPostingResult;
import com.multiassetoms.posttrade.model.AverageCostEntry;
import com.multiassetoms.posttrade.model.LedgerPostingException;
import com.multiassetoms.posttrade.model.LedgerPostingResult;
import com.multiassetoms.posttrade.model.RealizedPnlEntry;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostSettlementAccountingService {

    private final TradeRepository tradeRepository;
    private final PostSettlementLedgerService postSettlementLedgerService;
    private final AverageCostService averageCostService;
    private final RealizedPnlService realizedPnlService;

    public PostSettlementAccountingService(
            TradeRepository tradeRepository,
            PostSettlementLedgerService postSettlementLedgerService,
            AverageCostService averageCostService,
            RealizedPnlService realizedPnlService
    ) {
        this.tradeRepository = tradeRepository;
        this.postSettlementLedgerService = postSettlementLedgerService;
        this.averageCostService = averageCostService;
        this.realizedPnlService = realizedPnlService;
    }

    public AccountingPostingResult post(UUID tradeId) {
        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("trade not found"));
        validatePostable(trade);

        LedgerPostingResult ledgerPostingResult = postSettlementLedgerService.post(tradeId);
        AverageCostEntry averageCostEntry = averageCostService.post(tradeId);
        RealizedPnlEntry realizedPnlEntry = trade.side() == OrderSide.SELL
                ? realizedPnlService.postWithCurrentAverageCost(tradeId)
                : null;

        return new AccountingPostingResult(
                ledgerPostingResult.positionLedgerEntry(),
                ledgerPostingResult.cashLedgerEntry(),
                averageCostEntry,
                realizedPnlEntry
        );
    }

    private void validatePostable(Trade trade) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new LedgerPostingException("only SETTLED trades can be posted to accounting");
        }
        if (trade.grossNotional() == null) {
            throw new LedgerPostingException("grossNotional is required to post accounting");
        }
    }
}
