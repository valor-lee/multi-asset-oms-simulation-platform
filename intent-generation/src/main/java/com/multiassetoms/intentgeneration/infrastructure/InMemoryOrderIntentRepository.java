package com.multiassetoms.intentgeneration.infrastructure;

import com.multiassetoms.intentgeneration.application.OrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderIntentRepository implements OrderIntentRepository {

    private final Map<UUID, OrderIntent> intentsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> intentIdsByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public OrderIntent save(OrderIntent intent) {
        intentsById.put(intent.intentId(), intent);
        if (intent.idempotencyKey() != null) {
            intentIdsByIdempotencyKey.put(intent.idempotencyKey(), intent.intentId());
        }
        return intent;
    }

    @Override
    public Optional<OrderIntent> findByIntentId(UUID intentId) {
        return Optional.ofNullable(intentsById.get(intentId));
    }

    @Override
    public Optional<OrderIntent> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        UUID intentId = intentIdsByIdempotencyKey.get(idempotencyKey);
        if (intentId == null) {
            return Optional.empty();
        }
        return findByIntentId(intentId);
    }
}
