package ru.proxodnaya.people.exception;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ru.proxodnaya.common.exception.ConflictException;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.common.exception.ProblemDetailFactory;

/**
 * Ошибки API → {@code application/problem+json} с {@code code}; ошибки полей — в {@code errors[]}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException error) {
        return ProblemDetailFactory.of(error, HttpStatus.NOT_FOUND.value());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException error) {
        return ProblemDetailFactory.of(error, HttpStatus.CONFLICT.value());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException error) {
        return ProblemDetailFactory.of(error, statusOf(error.code()).value());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Проверьте заполнение полей");
        problem.setTitle(ErrorCode.VALIDATION_ERROR.code());
        problem.setProperty(CODE, ErrorCode.VALIDATION_ERROR.code());
        List<Map<String, String>> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> Map.of(
                        "field", field.getField(),
                        CODE, String.valueOf(field.getCode()),
                        "message", String.valueOf(field.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    private static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case WORKER_NOT_FOUND, NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DEVICE_ALREADY_BOUND, CONFLICT -> HttpStatus.CONFLICT;
            case INVITE_EXPIRED -> HttpStatus.GONE;
            case FORBIDDEN, DEVICE_UNKNOWN, DEVICE_REVOKED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
