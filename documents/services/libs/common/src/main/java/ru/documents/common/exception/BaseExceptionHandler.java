package ru.documents.common.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Единый формат ошибок: application/problem+json {type,title,status,detail,code,hint,errors?}.
 */
@RestControllerAdvice
public class BaseExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BaseExceptionHandler.class);
    private static final String CODE = "code";
    private static final String HINT = "hint";

    /** Доменные ошибки с кодом из API.md. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> domain(DomainException ex) {
        ProblemDetail pd = problem(ex.status(), ex.code(), ex.getMessage(), ex.hint());
        ex.extra().forEach(pd::setProperty);
        return respond(pd);
    }

    /** Ошибки валидации тела запроса. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(400, "VALIDATION_ERROR", "Проверьте заполнение полей", "Исправьте отмеченные поля");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return respond(pd);
    }

    /** Нет обязательного заголовка или части запроса. */
    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ProblemDetail> missing(Exception ex) {
        if (ex instanceof MissingServletRequestPartException) {
            return respond(problem(400, "FILE_EMPTY", "Файл пустой", "Выберите файл ещё раз"));
        }
        return respond(problem(400, "VALIDATION_ERROR", "Не хватает данных запроса: " + ex.getMessage(),
                "Обновите страницу и повторите"));
    }

    /** Слишком большой файл на уровне контейнера. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException ex) {
        return respond(problem(413, "FILE_TOO_LARGE", "Файл слишком большой",
                "Сожмите фото или загрузите PDF меньшего размера"));
    }

    /** Нет прав. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(AccessDeniedException ex) {
        return respond(problem(403, "FORBIDDEN", "Недостаточно прав", "Обратитесь к старшему оператору"));
    }

    /** Нет такого пути. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException ex) {
        return respond(problem(404, "NOT_FOUND", "Не найдено", "Проверьте ссылку"));
    }

    /** Всё остальное — 500 без подробностей наружу. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> other(RuntimeException ex) {
        LOG.error("unhandled error: {}", ex.getClass().getName(), ex);
        return respond(problem(500, "SYSTEM_ERROR", "Внутренняя ошибка", "Повторите позже"));
    }

    /**
     * Собирает ProblemDetail.
     *
     * @param status HTTP-статус
     * @param code   код из API.md
     * @param detail текст для человека
     * @param hint   что сделать
     * @return problem
     */
    public static ProblemDetail problem(int status, String code, String detail, String hint) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        pd.setType(URI.create("urn:documents:problem:" + code));
        pd.setTitle(code);
        pd.setProperty(CODE, code);
        if (hint != null) {
            pd.setProperty(HINT, hint);
        }
        return pd;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
