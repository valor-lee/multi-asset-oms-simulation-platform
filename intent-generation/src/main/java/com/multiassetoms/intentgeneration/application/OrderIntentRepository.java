package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.OrderIntent;

import java.util.Optional;
import java.util.UUID;

public interface OrderIntentRepository {

    OrderIntent save(OrderIntent intent);

    Optional<OrderIntent> findByIntentId(UUID intentId);

    Optional<OrderIntent> findByIdempotencyKey(String idempotencyKey);
}
