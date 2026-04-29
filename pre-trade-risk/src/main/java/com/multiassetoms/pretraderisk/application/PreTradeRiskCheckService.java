package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckCommand;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class PreTradeRiskCheckService {

    private final Clock clock;

    public PreTradeRiskCheckService() {
        this(Clock.systemUTC());
    }

    PreTradeRiskCheckService(Clock clock) {
        this.clock = clock;
    }

    public PreTradeRiskCheckResult check(PreTradeRiskCheckCommand command) {
        if (command.requestedQty() == null || command.requestedQty().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(command, "requestedQty must be greater than zero");
        }
        if (command.orderType() == OrderType.LIMIT && command.limitPrice() == null) {
            return reject(command, "limitPrice is required for LIMIT orders");
        }
        if (command.limitPrice() != null && command.limitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(command, "limitPrice must be greater than zero");
        }
        return approve(command);
    }

    private PreTradeRiskCheckResult approve(PreTradeRiskCheckCommand command) {
        return new PreTradeRiskCheckResult(
                command.intentId(),
                PreTradeRiskDecision.APPROVED,
                "approved",
                Instant.now(clock)
        );
    }

    private PreTradeRiskCheckResult reject(PreTradeRiskCheckCommand command, String reason) {
        return new PreTradeRiskCheckResult(
                command.intentId(),
                PreTradeRiskDecision.REJECTED,
                reason,
                Instant.now(clock)
        );
    }
}
