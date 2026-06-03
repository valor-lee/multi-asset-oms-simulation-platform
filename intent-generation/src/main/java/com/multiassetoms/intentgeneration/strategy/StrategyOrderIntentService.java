package com.multiassetoms.intentgeneration.strategy;

import com.multiassetoms.intentgeneration.application.OrderIntentCreator;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import org.springframework.stereotype.Service;

@Service
public class StrategyOrderIntentService {

    private final OrderIntentCreator orderIntentCreator;

    public StrategyOrderIntentService(OrderIntentCreator orderIntentCreator) {
        this.orderIntentCreator = orderIntentCreator;
    }

    public OrderIntent create(StrategyOrderIntentRequest request) {
        return orderIntentCreator.create(new CreateOrderIntentCommand(
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
