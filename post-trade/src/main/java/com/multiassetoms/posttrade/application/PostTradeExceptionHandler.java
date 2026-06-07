package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.posttrade.model.PostTradeRequestException;
import com.multiassetoms.posttrade.model.SettlementException;
import com.multiassetoms.posttrade.model.SettlementNotFoundException;
import com.multiassetoms.posttrade.model.TradeCaptureException;
import com.multiassetoms.posttrade.model.TradeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.posttrade")
public class PostTradeExceptionHandler {

    @ExceptionHandler(PostTradeRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlePostTradeRequestException(PostTradeRequestException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFoundException(OrderNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(TradeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTradeNotFoundException(TradeNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(SettlementNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSettlementNotFoundException(SettlementNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(TradeCaptureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTradeCaptureException(TradeCaptureException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(SettlementException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSettlementException(SettlementException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
