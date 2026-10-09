package ru.proxodnaya.shift.features.interval.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Решение о проходе из {@code access.pass.v1} ({@code PassDecided}).
 *
 * @param attemptId    попытка
 * @param workerId     рабочий или {@code null} (QR не разобран)
 * @param checkpointId проходная
 * @param zoneId       зона проходной или {@code null}
 * @param direction    IN | OUT
 * @param decision     ALLOW | DENY | MANUAL
 * @param reasonCode   код причины
 * @param source       KIOSK | MANUAL | SIMULATOR | …
 * @param ts           момент решения
 */
public record PassEvent(UUID attemptId, String workerId, String checkpointId, String zoneId, String direction, String decision,
                        String reasonCode, String source, Instant ts) {

    static final String SIMULATOR = "SIMULATOR";

    /**
     * Человек прошёл: ALLOW или ручной проход охранника.
     *
     * @return {@code true}, если проход состоялся
     */
    public boolean passed() {
        return workerId != null && ("ALLOW".equals(decision) || "MANUAL".equals(decision));
    }

    /**
     * Вход.
     *
     * @return {@code true} для IN
     */
    public boolean entry() {
        return "IN".equals(direction);
    }

    /**
     * Сгенерировано симулятором (FR-34) — по умолчанию в отчёты не попадает.
     *
     * @return {@code true} для SIMULATOR
     */
    public boolean simulated() {
        return SIMULATOR.equals(source);
    }
}
