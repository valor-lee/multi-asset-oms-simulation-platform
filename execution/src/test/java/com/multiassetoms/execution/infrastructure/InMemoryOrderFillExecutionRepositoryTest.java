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
                new BigDecimal("55000"),
                new BigDecimal("40"),
                new BigDecimal("10"),
                Instant.parse("2026-05-20T01:00:00Z")
        );

        repository.save(fillExecution);

        Optional<OrderFillExecution> found = repository.findByFillExecutionId(fillExecution.fillExecutionId());
        assertTrue(found.isPresent());
        assertEquals(fillExecution, found.get());
    }

    @Test
    void findsFillExecutionsByOrderId() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001002");
        OrderFillExecution firstFillExecution = new OrderFillExecution(
                UUID.fromString("00000000-0000-0000-0000-000000003002"),
                orderId,
                new BigDecimal("4"),
                new BigDecimal("55000"),
                new BigDecimal("40"),
                new BigDecimal("10"),
                Instant.parse("2026-05-20T01:00:00Z")
        );
        OrderFillExecution secondFillExecution = new OrderFillExecution(
                UUID.fromString("00000000-0000-0000-0000-000000003003"),
                orderId,
                new BigDecimal("6"),
                new BigDecimal("55500"),
                new BigDecimal("60"),
                new BigDecimal("15"),
                Instant.parse("2026-05-20T01:01:00Z")
        );

        repository.save(firstFillExecution);
        repository.save(secondFillExecution);

        assertEquals(2, repository.findByOrderId(orderId).size());
        assertTrue(repository.findByOrderId(orderId).contains(firstFillExecution));
        assertTrue(repository.findByOrderId(orderId).contains(secondFillExecution));
    }

    @Test
    void returnsEmptyWhenFillExecutionDoesNotExist() {
        Optional<OrderFillExecution> found = repository.findByFillExecutionId(
                UUID.fromString("00000000-0000-0000-0000-000000003099")
        );

        assertTrue(found.isEmpty());
    }
}
