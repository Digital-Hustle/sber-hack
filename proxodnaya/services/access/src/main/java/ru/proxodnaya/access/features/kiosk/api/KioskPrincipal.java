package ru.proxodnaya.access.features.kiosk.api;

import java.util.UUID;

/**
 * Терминал, чья подпись запроса проверена (ACC-12): принципал запросов {@code /kiosk/*}.
 *
 * @param kioskId терминал
 */
public record KioskPrincipal(UUID kioskId) {
}
