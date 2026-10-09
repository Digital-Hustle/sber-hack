package ru.proxodnaya.access.api.dto.response;

/**
 * Коды только для сопряжённого терминала, без исходных PIN.
 *
 * @param service сервисный, null — не задан
 * @param guard   охранника, null — не задан
 */
public record TerminalCodeHashesRs(TerminalCodeHashRs service, TerminalCodeHashRs guard) {
}
