package ru.proxodnaya.access.api.dto.response;

/**
 * Состояние кодов для кабинета.
 *
 * @param service сервисный, null — не задан
 * @param guard   охранника, null — не задан
 */
public record TerminalCodesRs(TerminalCodeInfoRs service, TerminalCodeInfoRs guard) {
}
