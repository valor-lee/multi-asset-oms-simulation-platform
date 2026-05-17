package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.intentgeneration.model.OrderIntent;

public record OrderConversionResult(
        Order order,
        OrderIntent intent
) {
}
