package ru.proxodnaya.access.exception;

import java.util.List;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
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
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.common.exception.ProblemDetailFactory;

/**
 * Доменные исключения и ошибки ввода → {@code application/problem+json} с {@code code}; ошибки полей — в {@code errors[]}.
 * Отказ в проходе сюда не попадает — это 200. Наследует {@link ResponseEntityExceptionHandler}, иначе стандартные
 * ошибки MVC перехватит автоконфигурация problemdetails и ответ останется без {@code code}.
 */
@RestControllerAdvice
public class DomainExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException error) {
        return ProblemDetailFactory.of(error, HttpStatus.NOT_FOUND.value());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException error) {
        if (error.code() == ErrorCode.FORBIDDEN) {
            return ProblemDetailFactory.of(error, HttpStatus.FORBIDDEN.value());
        }
        return ProblemDetailFactory.of(error, HttpStatus.BAD_REQUEST.value());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = validation("Проверьте заполнение полей");
        List<Map<String, String>> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> Map.of(
                        "field", field.getField(),
                        CODE, String.valueOf(field.getCode()),
                        "message", String.valueOf(field.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException error, HttpHeaders headers, HttpStatusCode status,
                                                        WebRequest request) {
        String name = error.getPropertyName() == null ? "" : " «" + error.getPropertyName() + "»";
        return ResponseEntity.badRequest().body(validation("Неверное значение параметра" + name));
    }

    private static ProblemDetail validation(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(ErrorCode.VALIDATION_ERROR.code());
        problem.setProperty(CODE, ErrorCode.VALIDATION_ERROR.code());
        return problem;
    }
}
