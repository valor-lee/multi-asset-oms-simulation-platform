package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.posttrade.model.TradeCaptureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.posttrade")
public class PostTradeExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFoundException(OrderNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(TradeCaptureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTradeCaptureException(TradeCaptureException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
