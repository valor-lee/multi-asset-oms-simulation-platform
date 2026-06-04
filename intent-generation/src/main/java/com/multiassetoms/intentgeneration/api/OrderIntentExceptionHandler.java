package com.multiassetoms.intentgeneration.api;

import com.multiassetoms.intentgeneration.model.OrderIntentIdempotencyConflictException;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderIntentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.intentgeneration")
public class OrderIntentExceptionHandler {

    @ExceptionHandler(OrderIntentValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(OrderIntentValidationException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderIntentIdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyConflictException(OrderIntentIdempotencyConflictException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderIntentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFoundException(OrderIntentNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
