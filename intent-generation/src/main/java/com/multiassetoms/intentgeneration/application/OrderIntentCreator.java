package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderIntentCreator {

    private final OrderIntentFactory orderIntentFactory;
    private final OrderIntentRepository orderIntentRepository;

    public OrderIntentCreator(
            OrderIntentFactory orderIntentFactory,
            OrderIntentRepository orderIntentRepository
    ) {
        this.orderIntentFactory = orderIntentFactory;
        this.orderIntentRepository = orderIntentRepository;
    }

    public synchronized OrderIntent create(CreateOrderIntentCommand command) {
        return findExistingIntent(command.idempotencyKey())
                .orElseGet(() -> orderIntentRepository.save(orderIntentFactory.create(command)));
    }

    private Optional<OrderIntent> findExistingIntent(String idempotencyKey) {
        String normalized = normalize(idempotencyKey);
        if (normalized == null) {
            return Optional.empty();
        }
        return orderIntentRepository.findByIdempotencyKey(normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
