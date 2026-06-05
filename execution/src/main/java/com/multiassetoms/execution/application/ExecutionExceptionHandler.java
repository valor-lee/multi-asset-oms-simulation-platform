package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.ExecutionRequestException;
import com.multiassetoms.execution.model.OrderAcknowledgementException;
import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.execution.model.OrderFillException;
import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.execution.model.OrderSubmissionException;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.multiassetoms.execution")
public class ExecutionExceptionHandler {

    @ExceptionHandler(ExecutionRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleExecutionRequestException(ExecutionRequestException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderIntentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderIntentNotFoundException(OrderIntentNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFoundException(OrderNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderConversionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderConversionException(OrderConversionException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderSubmissionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderSubmissionException(OrderSubmissionException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderAcknowledgementException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderAcknowledgementException(OrderAcknowledgementException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(OrderFillException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderFillException(OrderFillException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
