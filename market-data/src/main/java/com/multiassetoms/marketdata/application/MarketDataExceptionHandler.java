package com.multiassetoms.marketdata.application;

import com.multiassetoms.marketdata.model.MarketDataException;
import com.multiassetoms.marketdata.model.MarketPriceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.marketdata")
public class MarketDataExceptionHandler {

    @ExceptionHandler(MarketDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMarketDataException(MarketDataException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(MarketPriceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleMarketPriceNotFoundException(MarketPriceNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
