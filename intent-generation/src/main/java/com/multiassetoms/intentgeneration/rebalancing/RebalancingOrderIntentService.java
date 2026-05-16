package com.multiassetoms.intentgeneration.rebalancing;

import com.multiassetoms.intentgeneration.application.OrderIntentFactory;
import com.multiassetoms.intentgeneration.application.OrderIntentRepository;
import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import org.springframework.stereotype.Service;

@Service
public class RebalancingOrderIntentService {

    private final OrderIntentFactory orderIntentFactory;
    private final OrderIntentRepository orderIntentRepository;

    public RebalancingOrderIntentService(
            OrderIntentFactory orderIntentFactory,
            OrderIntentRepository orderIntentRepository
    ) {
        this.orderIntentFactory = orderIntentFactory;
        this.orderIntentRepository = orderIntentRepository;
    }

    public OrderIntent create(RebalancingOrderIntentRequest request) {
        OrderIntent intent = orderIntentFactory.create(new CreateOrderIntentCommand(
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
        return orderIntentRepository.save(intent);
    }
}
