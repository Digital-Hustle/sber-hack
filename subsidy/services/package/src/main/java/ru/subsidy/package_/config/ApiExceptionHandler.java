package ru.subsidy.package_.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.exception.ProblemDetailFactory;

/**
 * Доменные ошибки → ProblemDetail с кодом из docs/API.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Map<ErrorCode, HttpStatus> STATUSES = Map.of(
            ErrorCode.INN_INVALID, HttpStatus.BAD_REQUEST,
            ErrorCode.PROGRAM_NOT_FOUND, HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
            ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.PACKAGE_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.CONFLICT, HttpStatus.CONFLICT,
            ErrorCode.PACKAGE_LOCKED, HttpStatus.CONFLICT,
            ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);

    /**
     * Доменное исключение.
     *
     * @param exception исключение
     * @return ProblemDetail
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> domain(DomainException exception) {
        HttpStatus status = STATUSES.getOrDefault(exception.code(), HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ProblemDetailFactory.of(exception, status.value()));
    }
}
