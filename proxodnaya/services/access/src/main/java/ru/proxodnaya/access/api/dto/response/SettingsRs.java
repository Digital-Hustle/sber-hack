package ru.proxodnaya.access.api.dto.response;

/**
 * Настройки прохода. Поля — как в {@code SettingsRq}.
 *
 * @param faceThreshold         порог похожести лица
 * @param qrToleranceSec        допуск часов телефона, с
 * @param shiftGraceMin         ранний вход до начала смены, мин
 * @param requireShift          пускать на вход только по смене
 * @param repeatScanCooldownSec пауза после прохода, с
 * @param presenceTtlHours      «забытый выход» через, ч
 * @param tempLockFailures      неудачных сверок до блокировки
 * @param tempLockWindowSec     окно подсчёта сверок, с
 * @param terminalMode          логика терминала
 * @param terminalScope         одна логика на все терминалы или своя у каждого
 * @param offlinePolicy         без связи
 * @param offlineMaxHours       автономная проверка, ч
 * @param offlineAfterExpiry    после срока автономности
 * @param offlineCheckShift     проверять смену по снимку
 * @param offlineUnknownDevice  телефон не в снимке
 * @param updatedAt             изменены, Unix ms, или {@code null} — значения по умолчанию
 * @param updatedBy             кем
 */
public record SettingsRs(double faceThreshold, int qrToleranceSec, int shiftGraceMin, boolean requireShift, int repeatScanCooldownSec,
                         int presenceTtlHours, int tempLockFailures, int tempLockWindowSec, String terminalMode, String terminalScope,
                         String offlinePolicy, int offlineMaxHours, String offlineAfterExpiry, boolean offlineCheckShift,
                         String offlineUnknownDevice, Long updatedAt, String updatedBy) {
}
