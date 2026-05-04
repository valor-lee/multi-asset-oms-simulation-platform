package com.multiassetoms.intentgeneration.manual;

import com.multiassetoms.intentgeneration.application.OrderIntentFactory;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import org.springframework.stereotype.Service;

@Service
public class ManualOrderIntentService {

    private final OrderIntentFactory orderIntentFactory;

    public ManualOrderIntentService(OrderIntentFactory orderIntentFactory) {
        this.orderIntentFactory = orderIntentFactory;
    }

    public OrderIntent create(ManualOrderIntentRequest request) {
        return orderIntentFactory.create(new CreateOrderIntentCommand(
                request.portfolioId(),
                request.instrumentId(),
                OrderIntentSourceType.MANUAL,
                null,
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
