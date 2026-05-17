package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrderRepositoryTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

    @Test
    void savesAndFindsOrderByOrderId() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                UUID.fromString("00000000-0000-0000-0000-000000000401")
        );

        repository.save(order);

        assertEquals(order, repository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void savesAndFindsOrderByIntentId() {
        Order order = order(
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                UUID.fromString("00000000-0000-0000-0000-000000000402")
        );

        repository.save(order);

        assertEquals(order.orderId(), repository.findByIntentId(order.intentId()).orElseThrow().orderId());
    }

    @Test
    void returnsEmptyWhenIntentIdDoesNotExist() {
        assertTrue(repository.findByIntentId(UUID.fromString("00000000-0000-0000-0000-000000000499")).isEmpty());
    }

    private Order order(UUID orderId, UUID intentId) {
        Instant now = Instant.parse("2026-05-17T00:00:00Z");
        return new Order(
                orderId,
                intentId,
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                OrderStatus.CREATED,
                now,
                now
        );
    }
}
