package ru.proxodnaya.access.features.kiosk.model;

/**
 * Изменение терминала из кабинета; {@code null} — поле не меняется.
 *
 * @param name          название
 * @param checkpointId  проходная
 * @param mode          логика прохода
 * @param offlinePolicy политика без связи
 */
public record KioskPatch(String name, String checkpointId, String mode, String offlinePolicy) {
}
