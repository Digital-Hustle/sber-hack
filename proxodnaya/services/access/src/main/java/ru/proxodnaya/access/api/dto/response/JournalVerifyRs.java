package ru.proxodnaya.access.api.dto.response;

/**
 * Проверка целостности журнала.
 *
 * @param ok       цепочка цела
 * @param checked  проверено звеньев
 * @param brokenAt первая испорченная запись
 */
public record JournalVerifyRs(boolean ok, long checked, String brokenAt) {
}
