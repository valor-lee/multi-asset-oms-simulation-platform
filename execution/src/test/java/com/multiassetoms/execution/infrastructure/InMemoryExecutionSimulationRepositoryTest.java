package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.model.ExecutionSimulationResult;
import com.multiassetoms.execution.model.ExecutionSimulationStatus;
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

class InMemoryExecutionSimulationRepositoryTest {

    private final InMemoryExecutionSimulationRepository repository =
            new InMemoryExecutionSimulationRepository();

    @Test
    void savesAndFindsResultBySimulationId() {
        UUID simulationId = UUID.fromString("00000000-0000-0000-0000-000000075001");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000076001");
        ExecutionSimulationResult result = new ExecutionSimulationResult(
                simulationId,
                orderId,
                ExecutionSimulationStatus.FILLED,
                new BigDecimal("55000"),
                new BigDecimal("55550"),
                new BigDecimal("4"),
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                80L,
                order(orderId)
        );

        repository.save(result);

        assertEquals(result, repository.findBySimulationId(simulationId).orElseThrow());
    }

    private Order order(UUID orderId) {
        return new Order(
                orderId,
                UUID.randomUUID(),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("10"),
                new BigDecimal("4"),
                null,
                TimeInForce.DAY,
                OrderStatus.PARTIALLY_FILLED,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z")
        );
    }
}
