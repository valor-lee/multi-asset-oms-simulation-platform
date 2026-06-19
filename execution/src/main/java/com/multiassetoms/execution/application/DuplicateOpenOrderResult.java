package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;

import java.util.UUID;

public record DuplicateOpenOrderResult(
        boolean duplicateOpenOrderExists,
        UUID duplicateOpenOrderId,
        Order duplicateOpenOrder
) {

    public static DuplicateOpenOrderResult found(Order order) {
        return new DuplicateOpenOrderResult(true, order.orderId(), order);
    }

    public static DuplicateOpenOrderResult notFound() {
        return new DuplicateOpenOrderResult(false, null, null);
    }
}
