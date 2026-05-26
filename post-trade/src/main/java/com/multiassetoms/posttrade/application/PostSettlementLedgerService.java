package com.multiassetoms.posttrade.application;

import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.LedgerPostingException;
import com.multiassetoms.posttrade.model.LedgerPostingResult;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostSettlementLedgerService {

    private final TradeRepository tradeRepository;
    private final PositionLedgerService positionLedgerService;
    private final CashLedgerService cashLedgerService;

    public PostSettlementLedgerService(
            TradeRepository tradeRepository,
            PositionLedgerService positionLedgerService,
            CashLedgerService cashLedgerService
    ) {
        this.tradeRepository = tradeRepository;
        this.positionLedgerService = positionLedgerService;
        this.cashLedgerService = cashLedgerService;
    }

    /**
     * settled trade를 post-settlement 원장들에 함께 반영한다.
     * 상태별 처리:
     * - SETTLED: position ledger와 cash ledger에 posting
     * - 이미 posting된 trade: 각 ledger service의 idempotency에 따라 기존 entry 반환
     * - gross notional이 없는 trade: cash ledger posting 전 거절
     * - 그 외 상태: post-settlement ledger 반영 대상이 아니므로 예외
     *
     * @param tradeId ledger에 반영할 trade id
     * @return position ledger entry와 cash ledger entry
     */
    public LedgerPostingResult post(UUID tradeId) {
        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new LedgerPostingException("trade not found"));

        validatePostable(trade);
        return new LedgerPostingResult(
                positionLedgerService.post(trade.tradeId()),
                cashLedgerService.post(trade.tradeId())
        );
    }

    private void validatePostable(Trade trade) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new LedgerPostingException("only SETTLED trades can be posted to ledgers");
        }
        if (trade.grossNotional() == null) {
            throw new LedgerPostingException("grossNotional is required to post ledgers");
        }
    }
}
