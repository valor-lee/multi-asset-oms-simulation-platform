package com.multiassetoms.pretraderisk.application;

import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.pretraderisk.model.PreTradeRiskTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.pretraderisk")
public class PreTradeRiskExceptionHandler {

    @ExceptionHandler(OrderIntentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderIntentNotFoundException(OrderIntentNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(PreTradeRiskTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTransitionException(PreTradeRiskTransitionException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
