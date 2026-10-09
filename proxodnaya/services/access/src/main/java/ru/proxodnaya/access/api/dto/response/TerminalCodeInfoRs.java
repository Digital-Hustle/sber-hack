package ru.proxodnaya.access.api.dto.response;

/**
 * Публичные свойства кода для кабинета, без соли и хэша.
 *
 * @param kind      вид
 * @param digits    число цифр
 * @param updatedAt момент изменения в миллисекундах Unix
 * @param by        автор
 */
public record TerminalCodeInfoRs(String kind, int digits, long updatedAt, String by) {
}
