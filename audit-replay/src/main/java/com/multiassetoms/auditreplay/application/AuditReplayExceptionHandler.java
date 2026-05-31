package com.multiassetoms.auditreplay.application;

import com.multiassetoms.auditreplay.model.OrderReplayException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class AuditReplayExceptionHandler {

    @ExceptionHandler({
            OrderReplayException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuditReplayErrorResponse handleDomainException(RuntimeException exception) {
        return new AuditReplayErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuditReplayErrorResponse handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return new AuditReplayErrorResponse(exception.getParameterName() + " is required");
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuditReplayErrorResponse handleInvalidRequestArgument(Exception exception) {
        return new AuditReplayErrorResponse("invalid request argument");
    }
}
