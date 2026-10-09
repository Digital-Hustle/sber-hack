package ru.documents.review.features.staff.model;

/**
 * Выданный код: показывается старшему один раз (QR для приложения-аутентификатора), нигде не хранится.
 *
 * @param login      логин
 * @param otpauthUri ссылка otpauth://totp/… (её кодирует QR)
 * @param secret     секрет Base32 — для ручного ввода в приложение
 */
public record Enrollment(String login, String otpauthUri, String secret) {
}
