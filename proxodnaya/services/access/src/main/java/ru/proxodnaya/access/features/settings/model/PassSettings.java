package ru.proxodnaya.access.features.settings.model;

import java.time.Instant;

/**
 * Настройки прохода (ACC-18). Поля терминала и офлайна хранятся для киосков (снимок допусков, ACC-15).
 *
 * @param faceThreshold         порог похожести лица
 * @param qrToleranceSec        допуск часов телефона, с
 * @param shiftGraceMin         ранний вход до начала смены, мин (окно считает shift)
 * @param requireShift          пускать на вход только по смене
 * @param repeatScanCooldownSec пауза после прохода, с
 * @param presenceTtlHours      «забытый выход» через, ч
 * @param tempLockFailures      неудачных сверок до блокировки
 * @param tempLockWindowSec     окно подсчёта сверок, с
 * @param terminal              логика терминала
 * @param offline               работа без связи
 * @param updatedAt             изменены или {@code null} — значения из env
 * @param updatedBy             кем
 */
public record PassSettings(double faceThreshold, int qrToleranceSec, int shiftGraceMin, boolean requireShift, int repeatScanCooldownSec,
                           int presenceTtlHours, int tempLockFailures, int tempLockWindowSec, Terminal terminal, Offline offline,
                           Instant updatedAt, String updatedBy) {

    /**
     * Логика терминала (ADR-038 песочницы).
     *
     * @param mode  QR_FACE | FACE_FIRST | QR_ONLY
     * @param scope GLOBAL | PER_KIOSK
     */
    public record Terminal(String mode, String scope) {
    }

    /**
     * Без связи (ADR-042 песочницы). Автоматического пропуска без проверки нет ни в одном режиме.
     *
     * @param policy        GUARD | CLOSED | LOCAL
     * @param maxHours      сколько часов после снимка терминал проверяет сам (LOCAL)
     * @param afterExpiry   GUARD | CLOSED — когда срок истёк
     * @param checkShift    проверять смену по снимку
     * @param unknownDevice DENY — телефона нет в снимке → отказ
     */
    public record Offline(String policy, int maxHours, String afterExpiry, boolean checkShift, String unknownDevice) {
    }
}
