package ru.proxodnaya.access.features.terminalcode.model;

import java.time.Instant;

/**
 * Только солёный хэш локального кода терминала, не разрешение на проход.
 *
 * @param kind      service или guard
 * @param salt      соль base64url
 * @param hash      PBKDF2 base64url
 * @param iter      число итераций
 * @param digits    число цифр
 * @param updatedAt момент смены
 * @param by        автор из JWT
 */
public record TerminalCode(String kind, String salt, String hash, int iter, int digits, Instant updatedAt, String by) {
}
