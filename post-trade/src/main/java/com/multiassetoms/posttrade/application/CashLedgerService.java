package com.multiassetoms.posttrade.application;

import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.application.port.CashLedgerRepository;
import com.multiassetoms.posttrade.application.port.TradeRepository;
import com.multiassetoms.posttrade.model.CashLedgerEntry;
import com.multiassetoms.posttrade.model.CashLedgerException;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CashLedgerService {

    private final TradeRepository tradeRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final Clock clock;

    public CashLedgerService(
            TradeRepository tradeRepository,
            CashLedgerRepository cashLedgerRepository,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.cashLedgerRepository = cashLedgerRepository;
        this.clock = clock;
    }

    /**
     * settled trade를 cash ledger에 반영한다.
     * 상태별 처리:
     * - SETTLED: BUY는 음수, SELL은 양수 cash delta로 posting
     * - 이미 posting된 trade: 중복 요청으로 보고 기존 ledger entry 반환
     * - gross notional이 없는 trade: 현금 반영 금액을 알 수 없으므로 예외
     * - 그 외 상태: cash 반영 대상이 아니므로 예외
     *
     * @param tradeId cash ledger에 반영할 trade id
     * @return 생성되었거나 이미 존재하는 cash ledger entry
     */
    public CashLedgerEntry post(UUID tradeId) {
        CashLedgerEntry existingEntry = cashLedgerRepository.findByTradeId(tradeId)
                .orElse(null);
        if (existingEntry != null) {
            return existingEntry;
        }

        Trade trade = tradeRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new CashLedgerException("trade not found"));

        validatePostable(trade);
        return cashLedgerRepository.save(toLedgerEntry(trade, Instant.now(clock)));
    }

    /**
     * portfolio 기준 현재 cash balance를 조회한다.
     *
     * @param portfolioId portfolio id
     * @return 현재 cash balance
     */
    public BigDecimal currentCash(String portfolioId) {
        return cashLedgerRepository.currentCash(portfolioId);
    }

    private void validatePostable(Trade trade) {
        if (trade.status() != TradeStatus.SETTLED) {
            throw new CashLedgerException("only SETTLED trades can be posted to cash ledger");
        }
        if (trade.grossNotional() == null) {
            throw new CashLedgerException("grossNotional is required to post cash ledger");
        }
    }

    private CashLedgerEntry toLedgerEntry(Trade trade, Instant postedAt) {
        return new CashLedgerEntry(
                UUID.randomUUID(),
                trade.tradeId(),
                trade.portfolioId(),
                trade.side(),
                cashDelta(trade),
                postedAt
        );
    }

    private BigDecimal cashDelta(Trade trade) {
        BigDecimal feeAmount = trade.feeAmount() == null ? BigDecimal.ZERO : trade.feeAmount();
        if (trade.side() == OrderSide.BUY) {
            return trade.grossNotional().add(feeAmount).negate();
        }
        return trade.grossNotional().subtract(feeAmount);
    }
}
