package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOrderIntentResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskTransitionException;
import org.springframework.stereotype.Service;

@Service
public class PreTradeRiskOrderIntentService {

    private final PreTradeRiskCheckService riskCheckService;

    public PreTradeRiskOrderIntentService(PreTradeRiskCheckService riskCheckService) {
        this.riskCheckService = riskCheckService;
    }

    /**
     * 기본 risk check context로 order intent를 평가하고 risk 결과에 따른 다음 상태 intent를 반환한다.
     *
     * @param intent pre-trade risk 평가 대상 order intent
     * @return risk 평가 결과와 상태 전이된 새 order intent
     */
    public PreTradeRiskOrderIntentResult evaluate(OrderIntent intent) {
        return evaluate(intent, PreTradeRiskCheckContext.empty());
    }

    /**
     * 전달받은 risk check context로 order intent를 평가하고 risk 결과에 따른 다음 상태 intent를 반환한다.
     *
     * @param intent pre-trade risk 평가 대상 order intent. CREATED 상태만 허용한다.
     * @param checkContext limit, exposure, open order, market, control 정보를 담은 risk 평가 context
     * @return risk 평가 결과와 상태 전이된 새 order intent
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
        return new PreTradeRiskOrderIntentResult(
                transition(intent, riskCheckResult),
                riskCheckResult
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
