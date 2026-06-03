package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentIdempotencyConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
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
        Optional<OrderIntent> existingIntent = findExistingIntent(command.idempotencyKey());
        if (existingIntent.isPresent()) {
            return resolveExistingIntent(existingIntent.get(), command);
        }
        return orderIntentRepository.save(orderIntentFactory.create(command));
    }

    private Optional<OrderIntent> findExistingIntent(String idempotencyKey) {
        String normalized = normalize(idempotencyKey);
        if (normalized == null) {
            return Optional.empty();
        }
        return orderIntentRepository.findByIdempotencyKey(normalized);
    }

    private OrderIntent resolveExistingIntent(OrderIntent existingIntent, CreateOrderIntentCommand command) {
        if (!matches(existingIntent, command)) {
            throw new OrderIntentIdempotencyConflictException(
                    "idempotencyKey already exists for a different order intent request"
            );
        }
        return existingIntent;
    }

    private boolean matches(OrderIntent existingIntent, CreateOrderIntentCommand command) {
        return Objects.equals(existingIntent.portfolioId(), normalize(command.portfolioId()))
                && Objects.equals(existingIntent.instrumentId(), normalize(command.instrumentId()))
                && existingIntent.sourceType() == command.sourceType()
                && Objects.equals(existingIntent.sourceRefId(), normalize(command.sourceRefId()))
                && existingIntent.side() == command.side()
                && existingIntent.orderType() == command.orderType()
                && sameNumber(existingIntent.requestedQty(), command.requestedQty())
                && sameNumber(existingIntent.limitPrice(), command.limitPrice())
                && existingIntent.timeInForce() == command.timeInForce()
                && Objects.equals(existingIntent.reason(), normalize(command.reason()))
                && Objects.equals(existingIntent.createdBy(), normalize(command.createdBy()));
    }

    private boolean sameNumber(BigDecimal left, BigDecimal right) {
        BigDecimal normalizedLeft = normalizeScale(left);
        BigDecimal normalizedRight = normalizeScale(right);
        return Objects.equals(normalizedLeft, normalizedRight);
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
