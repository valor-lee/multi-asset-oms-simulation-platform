package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderIntentQueryService {

    private final OrderIntentRepository orderIntentRepository;

    public OrderIntentQueryService(OrderIntentRepository orderIntentRepository) {
        this.orderIntentRepository = orderIntentRepository;
    }

    public OrderIntent getByIntentId(UUID intentId) {
        return orderIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new OrderIntentNotFoundException("order intent not found"));
    }

    public OrderIntent getByIdempotencyKey(String idempotencyKey) {
        return orderIntentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new OrderIntentNotFoundException("order intent not found"));
    }
}
