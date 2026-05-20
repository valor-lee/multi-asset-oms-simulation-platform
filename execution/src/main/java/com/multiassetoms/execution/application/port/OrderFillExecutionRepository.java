package com.multiassetoms.execution.application.port;

import com.multiassetoms.execution.model.OrderFillExecution;

import java.util.Optional;
import java.util.UUID;

public interface OrderFillExecutionRepository {

    OrderFillExecution save(OrderFillExecution fillExecution);

    Optional<OrderFillExecution> findByFillExecutionId(UUID fillExecutionId);
}
