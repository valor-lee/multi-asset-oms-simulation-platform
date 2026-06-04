package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.execution")
public class ExecutionExceptionHandler {

    @ExceptionHandler(OrderIntentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderIntentNotFoundException(OrderIntentNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderConversionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderConversionException(OrderConversionException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
