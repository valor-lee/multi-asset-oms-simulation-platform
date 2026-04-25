package com.multiassetoms.intentgeneration.strategy;

import com.multiassetoms.intentgeneration.application.OrderIntentFactory;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import org.springframework.stereotype.Service;

@Service
public class StrategyOrderIntentService {

    private final OrderIntentFactory orderIntentFactory;

    public StrategyOrderIntentService(OrderIntentFactory orderIntentFactory) {
        this.orderIntentFactory = orderIntentFactory;
    }

    public OrderIntent create(StrategyOrderIntentRequest request) {
        return orderIntentFactory.create(new CreateOrderIntentCommand(
                request.portfolioId(),
                request.instrumentId(),
                OrderIntentSourceType.STRATEGY,
                request.strategySignalId(),
                request.side(),
                request.orderType(),
                request.requestedQty(),
                request.limitPrice(),
                request.timeInForce(),
                request.reason(),
                request.idempotencyKey(),
                request.createdBy()
        ));
    }
}
