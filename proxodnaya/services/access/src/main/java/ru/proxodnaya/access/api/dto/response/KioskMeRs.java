package ru.proxodnaya.access.api.dto.response;

/**
 * Состояние терминала для него самого (пульс раз в 5–30 с).
 *
 * @param kiosk      терминал
 * @param checkpoint проходная или {@code null} — не привязан
 * @param settings   логика прохода и политика без связи
 * @param codes      проверочные хэши только сопряжённому терминалу
 */
public record KioskMeRs(KioskRs kiosk, KioskCheckpointRs checkpoint, SettingsRs settings, TerminalCodeHashesRs codes) {
}
