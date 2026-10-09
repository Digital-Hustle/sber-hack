package ru.proxodnaya.access.api.dto.response;

/**
 * Проверочный хэш только для сопряжённого терминала.
 *
 * @param salt      соль base64url
 * @param hash      PBKDF2 base64url
 * @param iter      число итераций
 * @param digits    число цифр
 * @param updatedAt момент изменения в миллисекундах Unix
 * @param by        автор
 */
public record TerminalCodeHashRs(String salt, String hash, int iter, int digits, long updatedAt, String by) {
}
