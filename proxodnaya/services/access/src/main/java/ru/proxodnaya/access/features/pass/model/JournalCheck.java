package ru.proxodnaya.access.features.pass.model;

import java.util.UUID;

/**
 * Результат проверки целостности журнала.
 *
 * @param ok       цепочка цела
 * @param checked  сколько звеньев проверено
 * @param brokenAt первая испорченная запись или пусто
 */
public record JournalCheck(boolean ok, long checked, UUID brokenAt) {
}
