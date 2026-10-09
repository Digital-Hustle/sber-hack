package ru.proxodnaya.access.api.dto.response;

/**
 * Зарегистрированный терминал.
 *
 * @param kioskId  id (им терминал подписывает запросы)
 * @param pairCode код сопряжения на экране
 */
public record RegisterKioskRs(String kioskId, String pairCode) {
}
