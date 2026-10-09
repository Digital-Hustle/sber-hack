package ru.documents.docproc.features.processing.gateway;

/**
 * Зависимость (intake, ocr) недоступна или ответила ошибкой — стадию повторим с паузой.
 */
public class DependencyException extends RuntimeException {

    private final String code;

    /**
     * Ошибка зависимости.
     *
     * @param code  код (OCR_UNAVAILABLE, INTAKE_UNAVAILABLE, ...)
     * @param cause причина
     */
    public DependencyException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    /**
     * Код ошибки.
     *
     * @return код
     */
    public String code() {
        return code;
    }
}
