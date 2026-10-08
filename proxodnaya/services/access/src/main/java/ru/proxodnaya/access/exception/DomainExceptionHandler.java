package ru.proxodnaya.access.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Обработчик доменных исключений → ProblemDetail (RFC 7807).
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setProperty("code", ex.code().code());
        return pd;
    }
}
