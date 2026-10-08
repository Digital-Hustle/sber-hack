package ru.proxodnaya.access.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.common.exception.ProblemDetailFactory;

/**
 * Доменные исключения → {@code application/problem+json} с {@code code}. Отказ в проходе сюда не попадает — это 200.
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException error) {
        return ProblemDetailFactory.of(error, HttpStatus.NOT_FOUND.value());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException error) {
        return ProblemDetailFactory.of(error, HttpStatus.BAD_REQUEST.value());
    }
}
