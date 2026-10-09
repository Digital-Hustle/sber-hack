package ru.subsidy.package_.upload;

/**
 * Коды отказа приёма файла (docs/API.md).
 */
public enum RejectReason {
    /** Пустой файл. */
    FILE_EMPTY,
    /** Больше лимита. */
    FILE_TOO_LARGE,
    /** Формат не поддерживается. */
    FILE_TYPE_UNSUPPORTED,
    /** Не открывается. */
    FILE_CORRUPTED,
    /** PDF под паролем. */
    PDF_ENCRYPTED,
    /** Слишком много страниц. */
    TOO_MANY_PAGES
}
