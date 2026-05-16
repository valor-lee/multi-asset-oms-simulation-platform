package com.multiassetoms.intentgeneration.manual;

import com.multiassetoms.intentgeneration.application.OrderIntentFactory;
import com.multiassetoms.intentgeneration.infrastructure.InMemoryOrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualOrderIntentServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final OrderIntentFactory orderIntentFactory = new OrderIntentFactory(validator);
    private final InMemoryOrderIntentRepository repository = new InMemoryOrderIntentRepository();
    private final ManualOrderIntentService service = new ManualOrderIntentService(orderIntentFactory, repository);

    @Test
    void createsAndStoresManualOrderIntent() {
        OrderIntent intent = service.create(new ManualOrderIntentRequest(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "operator order",
                "manual-key-1",
                "operator"
        ));

        assertEquals(OrderIntentSourceType.MANUAL, intent.sourceType());
        assertEquals(OrderIntentStatus.CREATED, intent.status());
        assertTrue(repository.findByIntentId(intent.intentId()).isPresent());
        assertEquals(intent, repository.findByIdempotencyKey("manual-key-1").orElseThrow());
    }
}
