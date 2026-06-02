package com.multiassetoms.intentgeneration.rebalancing;

import com.multiassetoms.intentgeneration.application.OrderIntentCreator;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import org.springframework.stereotype.Service;

@Service
public class RebalancingOrderIntentService {

    private final OrderIntentCreator orderIntentCreator;

    public RebalancingOrderIntentService(OrderIntentCreator orderIntentCreator) {
        this.orderIntentCreator = orderIntentCreator;
    }

    public OrderIntent create(RebalancingOrderIntentRequest request) {
        return orderIntentCreator.create(new CreateOrderIntentCommand(
                request.portfolioId(),
                request.instrumentId(),
                OrderIntentSourceType.REBALANCING,
                request.rebalanceRunId(),
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
