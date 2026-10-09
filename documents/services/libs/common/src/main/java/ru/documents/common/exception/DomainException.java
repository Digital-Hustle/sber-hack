package ru.documents.common.exception;

import java.util.Map;

/**
 * Доменная ошибка: превращается в RFC 7807 problem+json с полями code, detail, hint (docs/API.md).
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;
    private final String hint;
    private final transient Map<String, Object> extra;

    public DomainException(String code, int status, String detail, String hint) {
        this(code, status, detail, hint, Map.of());
    }

    public DomainException(String code, int status, String detail, String hint, Map<String, Object> extra) {
        super(detail);
        this.code = code;
        this.status = status;
        this.hint = hint;
        this.extra = extra;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String hint() {
        return hint;
    }

    public Map<String, Object> extra() {
        return extra;
    }

    /** 404 без раскрытия, существует ли объект. */
    public static DomainException notFound() {
        return new DomainException("NOT_FOUND", 404, "Не найдено", "Проверьте ссылку");
    }

    /** 409 конфликт состояния. */
    public static DomainException conflict(String code, String detail, String hint) {
        return new DomainException(code, 409, detail, hint);
    }

    /** 400 ошибка запроса. */
    public static DomainException badRequest(String code, String detail, String hint) {
        return new DomainException(code, 400, detail, hint);
    }
}
