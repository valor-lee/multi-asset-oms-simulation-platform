package com.multiassetoms.intentgeneration.application;

import com.multiassetoms.intentgeneration.model.CreateOrderIntentCommand;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderIntentValidationException;
import com.multiassetoms.intentgeneration.model.OrderType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 의도 생성 단일 진입점
 */
@Component
public class OrderIntentFactory {

    private final Validator validator;
    private final Clock clock;

    public OrderIntentFactory(Validator validator) {
        this(validator, Clock.systemUTC());
    }

    OrderIntentFactory(Validator validator, Clock clock) {
        this.validator = validator;
        this.clock = clock;
    }

    public OrderIntent create(CreateOrderIntentCommand command) {
        validateBeanConstraints(command);
        validateBusinessRules(command);

        Instant now = Instant.now(clock);
        return new OrderIntent(
                UUID.randomUUID(),
                command.portfolioId(),
                command.instrumentId(),
                command.sourceType(),
                normalize(command.sourceRefId()),
                command.side(),
                command.orderType(),
                normalizeScale(command.requestedQty()),
                normalizeScale(command.limitPrice()),
                command.timeInForce(),
                normalize(command.reason()),
                OrderIntentStatus.CREATED,
                resolveIdempotencyKey(command),
                normalize(command.createdBy()),
                now,
                now
        );
    }

    /**
     * Bean Validation으로 필수값과 기본 제약을 검사
     * model 각 feild에 선언된 제약을 검증
     *
     * ConstraintViolation
     * - 어떤 객체를 검사했는지
     * - 어느 필드가 문제인지 (propertyPath)
     * - 어떤 메시지인지 (message)
     * - 실제 잘못된 값이 무엇인지 (invalidValue)
     * - 어떤 constraint 애노테이션이 실패했는지
     */
    private void validateBeanConstraints(CreateOrderIntentCommand command) {
        Set<ConstraintViolation<CreateOrderIntentCommand>> violations =
                                                    validator.validate(command);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                .map(violation ->
                    violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
            throw new OrderIntentValidationException(message);
        }
    }

    /**
     * 주문타입별 비지니스 규칙 검사
     */
    private void validateBusinessRules(CreateOrderIntentCommand command) {
        if (command.orderType() == OrderType.LIMIT && command.limitPrice() == null) {
            throw new OrderIntentValidationException("limitPrice is required for LIMIT orders");
        }
        if (command.orderType() == OrderType.MARKET && command.limitPrice() != null) {
            throw new OrderIntentValidationException("limitPrice must be null for MARKET orders");
        }
        if (command.limitPrice() != null && command.limitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderIntentValidationException("limitPrice must be greater than zero");
        }
    }

    private String resolveIdempotencyKey(CreateOrderIntentCommand command) {
        String normalized = normalize(command.idempotencyKey());
        return normalized != null ? normalized : UUID.randomUUID().toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}