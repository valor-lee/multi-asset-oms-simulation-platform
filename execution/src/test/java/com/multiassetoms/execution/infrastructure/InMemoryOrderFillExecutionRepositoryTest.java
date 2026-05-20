package com.multiassetoms.execution.infrastructure;

import com.multiassetoms.execution.model.OrderFillExecution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrderFillExecutionRepositoryTest {

    private final InMemoryOrderFillExecutionRepository repository =
            new InMemoryOrderFillExecutionRepository();

    @Test
    void savesAndFindsFillExecutionById() {
        OrderFillExecution fillExecution = new OrderFillExecution(
                UUID.fromString("00000000-0000-0000-0000-000000003001"),
                UUID.fromString("00000000-0000-0000-0000-000000001001"),
                new BigDecimal("4"),
                Instant.parse("2026-05-20T01:00:00Z")
        );

        repository.save(fillExecution);

        Optional<OrderFillExecution> found = repository.findByFillExecutionId(fillExecution.fillExecutionId());
        assertTrue(found.isPresent());
        assertEquals(fillExecution, found.get());
    }

    @Test
    void returnsEmptyWhenFillExecutionDoesNotExist() {
        Optional<OrderFillExecution> found = repository.findByFillExecutionId(
                UUID.fromString("00000000-0000-0000-0000-000000003099")
        );

        assertTrue(found.isEmpty());
    }
}
