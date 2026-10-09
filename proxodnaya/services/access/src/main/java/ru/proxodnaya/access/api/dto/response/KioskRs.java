package ru.proxodnaya.access.api.dto.response;

/**
 * Терминал. «В сети» — {@code lastSeen} моложе 45 с.
 *
 * @param id            id терминала
 * @param name          название
 * @param checkpointId  проходная или {@code null} — не привязан
 * @param mode          своя логика прохода или {@code null}
 * @param offlinePolicy своя политика без связи или {@code null}
 * @param pairCode      код сопряжения, пока не привязан
 * @param pairedAt      сопряжён, Unix ms
 * @param lastSeen      последний запрос, Unix ms
 * @param snapshotAt    последний офлайн-снимок, Unix ms (ACC-15)
 * @param chain         последнее звено журнала без связи (ACC-15)
 * @param syncError     последняя ошибка досылки (ACC-15)
 */
public record KioskRs(String id, String name, String checkpointId, String mode, String offlinePolicy, String pairCode,
                      Long pairedAt, Long lastSeen, Long snapshotAt, ChainRs chain, SyncErrorRs syncError) {

    /**
     * Звено цепочки журнала.
     *
     * @param seq  номер
     * @param hash хэш
     */
    public record ChainRs(long seq, String hash) {
    }

    /**
     * Ошибка досылки.
     *
     * @param at      когда, Unix ms
     * @param message что
     */
    public record SyncErrorRs(long at, String message) {
    }
}
