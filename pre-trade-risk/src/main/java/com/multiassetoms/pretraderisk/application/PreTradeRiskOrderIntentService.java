package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.marketdata.application.MarketPriceService;
import com.multiassetoms.marketdata.model.MarketPrice;
import com.multiassetoms.intentgeneration.application.OrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskMarketContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOrderIntentResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskRequestException;
import com.multiassetoms.pretraderisk.model.PreTradeRiskTransitionException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PreTradeRiskOrderIntentService {

    private final PreTradeRiskCheckService riskCheckService;
    private final OrderIntentRepository orderIntentRepository;
    private final MarketPriceService marketPriceService;

    public PreTradeRiskOrderIntentService(
            PreTradeRiskCheckService riskCheckService,
            OrderIntentRepository orderIntentRepository,
            MarketPriceService marketPriceService
    ) {
        this.riskCheckService = riskCheckService;
        this.orderIntentRepository = orderIntentRepository;
        this.marketPriceService = marketPriceService;
    }

    /**
     * 기본 risk check context로 order intent를 평가하고 risk 결과에 따른 다음 상태 intent를 저장한다.
     *
     * @param intent pre-trade risk 평가 대상 order intent
     * @return risk 평가 결과와 저장된 다음 상태 order intent
     */
    public PreTradeRiskOrderIntentResult evaluate(OrderIntent intent) {
        return evaluate(intent, PreTradeRiskCheckContext.empty());
    }

    /**
     * 전달받은 risk check context로 order intent를 평가하고 risk 결과에 따른 다음 상태 intent를 저장한다.
     *
     * @param intent pre-trade risk 평가 대상 order intent. CREATED 상태만 허용한다.
     * @param checkContext limit, exposure, open order, market, control 정보를 담은 risk 평가 context
     * @return risk 평가 결과와 저장된 다음 상태 order intent
     */
    public PreTradeRiskOrderIntentResult evaluate(
            OrderIntent intent,
            PreTradeRiskCheckContext checkContext
    ) {
        validateCreated(intent);

        PreTradeRiskCheckResult riskCheckResult = riskCheckService.evaluateWithContext(
                PreTradeRiskCheckCommand.from(intent),
                checkContext
        );
        OrderIntent transitionedIntent = transition(intent, riskCheckResult);
        OrderIntent savedIntent = orderIntentRepository.save(transitionedIntent);

        return new PreTradeRiskOrderIntentResult(savedIntent, riskCheckResult);
    }

    /**
     * market-data에 저장된 최신 가격을 기준으로 price band context를 구성한 뒤 risk 평가를 수행한다.
     * priceBandRate가 0.10이면 latest price 기준 -10% ~ +10%를 허용 가격 구간으로 본다.
     *
     * @param intent pre-trade risk 평가 대상 order intent. CREATED 상태만 허용한다.
     * @param baseContext market context 외 나머지 limit, exposure, open order, control context
     * @param priceBandRate latest price 기준 허용 가격 비율
     * @return risk 평가 결과와 저장된 다음 상태 order intent
     */
    public PreTradeRiskOrderIntentResult evaluateWithLatestPriceBand(
            OrderIntent intent,
            PreTradeRiskCheckContext baseContext,
            BigDecimal priceBandRate
    ) {
        validateCreated(intent);
        validatePriceBandRate(priceBandRate);
        MarketPrice latestPrice = marketPriceService.latestPrice(intent.instrumentId());
        PreTradeRiskCheckContext mergedContext = withMarketContext(
                baseContext,
                priceBandContext(latestPrice.price(), priceBandRate)
        );
        return evaluate(intent, mergedContext);
    }

    private void validatePriceBandRate(BigDecimal priceBandRate) {
        if (priceBandRate == null) {
            throw new PreTradeRiskRequestException("priceBandRate is required");
        }
        if (priceBandRate.compareTo(BigDecimal.ZERO) < 0
                || priceBandRate.compareTo(BigDecimal.ONE) > 0) {
            throw new PreTradeRiskRequestException("priceBandRate must be between 0 and 1");
        }
    }

    private PreTradeRiskMarketContext priceBandContext(
            BigDecimal latestPrice,
            BigDecimal priceBandRate
    ) {
        BigDecimal lowerPriceBand = latestPrice.multiply(BigDecimal.ONE.subtract(priceBandRate));
        BigDecimal upperPriceBand = latestPrice.multiply(BigDecimal.ONE.add(priceBandRate));
        return new PreTradeRiskMarketContext(lowerPriceBand, upperPriceBand);
    }

    private PreTradeRiskCheckContext withMarketContext(
            PreTradeRiskCheckContext baseContext,
            PreTradeRiskMarketContext marketContext
    ) {
        PreTradeRiskCheckContext safeBaseContext = baseContext == null
                ? PreTradeRiskCheckContext.empty()
                : baseContext;
        return new PreTradeRiskCheckContext(
                safeBaseContext.limitContext(),
                safeBaseContext.exposureContext(),
                safeBaseContext.openOrderContext(),
                marketContext,
                safeBaseContext.controlContext()
        );
    }

    /**
     * pre-trade risk 평가는 아직 risk 평가를 받지 않은 CREATED intent에만 허용한다.
     *
     * @param intent 상태 전이 가능 여부를 확인할 order intent
     */
    private void validateCreated(OrderIntent intent) {
        if (intent.status() != OrderIntentStatus.CREATED) {
            throw new PreTradeRiskTransitionException(
                    "only CREATED order intents can be evaluated by pre-trade risk"
            );
        }
    }

    /**
     * risk 평가 결과가 반영된 다음 상태의 intent 스냅샷을 만든다.
     *
     * @param intent risk 평가 전 원본 order intent. 기존 객체는 변경하지 않는다.
     * @param riskCheckResult intent에 대한 pre-trade risk 평가 결과
     * @return risk decision에 따라 RISK_APPROVED 또는 RISK_REJECTED 상태가 반영된 새 order intent
     */
    private OrderIntent transition(
            OrderIntent intent,
            PreTradeRiskCheckResult riskCheckResult
    ) {
        OrderIntentStatus nextStatus = riskCheckResult.decision() == PreTradeRiskDecision.APPROVED
                ? OrderIntentStatus.RISK_APPROVED
                : OrderIntentStatus.RISK_REJECTED;

        return new OrderIntent(
                intent.intentId(),
                intent.portfolioId(),
                intent.instrumentId(),
                intent.sourceType(),
                intent.sourceRefId(),
                intent.side(),
                intent.orderType(),
                intent.requestedQty(),
                intent.limitPrice(),
                intent.timeInForce(),
                intent.reason(),
                nextStatus,
                intent.idempotencyKey(),
                intent.createdBy(),
                intent.createdAt(),
                riskCheckResult.checkedAt()
        );
    }
}
