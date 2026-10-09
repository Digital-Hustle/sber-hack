package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Настройки прохода (PUT целиком).
 *
 * @param faceThreshold         порог похожести лица, 0.3–0.9
 * @param qrToleranceSec        допуск часов телефона, 0–300 с
 * @param shiftGraceMin         ранний вход до начала смены, 0–240 мин
 * @param requireShift          пускать на вход только по смене
 * @param repeatScanCooldownSec пауза после прохода, 0–600 с
 * @param presenceTtlHours      «забытый выход» через, 1–48 ч
 * @param tempLockFailures      неудачных сверок до блокировки, 1–20
 * @param tempLockWindowSec     окно подсчёта сверок, 60–3600 с
 * @param terminalMode          QR_FACE | FACE_FIRST | QR_ONLY
 * @param terminalScope         GLOBAL | PER_KIOSK
 * @param offlinePolicy         GUARD | CLOSED | LOCAL
 * @param offlineMaxHours       автономная проверка после снимка, 1–72 ч
 * @param offlineAfterExpiry    GUARD | CLOSED
 * @param offlineCheckShift     проверять смену по снимку
 * @param offlineUnknownDevice  DENY (телефона нет в снимке — отказ)
 */
public record SettingsRq(
        @NotNull @DecimalMin(value = "0.3", message = "Порог лица — от 0.3 до 0.9")
        @DecimalMax(value = "0.9", message = "Порог лица — от 0.3 до 0.9") Double faceThreshold,
        @NotNull @Min(value = 0, message = "Допуск QR — от 0 до 300 с") @Max(value = QR_MAX, message = "Допуск QR — от 0 до 300 с")
        Integer qrToleranceSec,
        @NotNull @Min(value = 0, message = "Ранний вход — от 0 до 240 мин")
        @Max(value = GRACE_MAX, message = "Ранний вход — от 0 до 240 мин") Integer shiftGraceMin,
        @NotNull Boolean requireShift,
        @NotNull @Min(value = 0, message = "Пауза — от 0 до 600 с") @Max(value = COOLDOWN_MAX, message = "Пауза — от 0 до 600 с")
        Integer repeatScanCooldownSec,
        @NotNull @Min(value = 1, message = "Срок присутствия — от 1 до 48 ч")
        @Max(value = PRESENCE_MAX, message = "Срок присутствия — от 1 до 48 ч") Integer presenceTtlHours,
        @NotNull @Min(value = 1, message = "Попыток — от 1 до 20") @Max(value = FAILURES_MAX, message = "Попыток — от 1 до 20")
        Integer tempLockFailures,
        @NotNull @Min(value = WINDOW_MIN, message = "Окно — от 60 до 3600 с") @Max(value = WINDOW_MAX, message = "Окно — от 60 до 3600 с")
        Integer tempLockWindowSec,
        @NotNull @Pattern(regexp = "QR_FACE|FACE_FIRST|QR_ONLY", message = "Режим терминала: QR_FACE, FACE_FIRST или QR_ONLY")
        String terminalMode,
        @NotNull @Pattern(regexp = "GLOBAL|PER_KIOSK", message = "Область: GLOBAL или PER_KIOSK") String terminalScope,
        @NotNull @Pattern(regexp = "GUARD|CLOSED|LOCAL", message = "Без связи: GUARD, CLOSED или LOCAL") String offlinePolicy,
        @NotNull @Min(value = 1, message = "Автономно — от 1 до 72 ч") @Max(value = OFFLINE_MAX, message = "Автономно — от 1 до 72 ч")
        Integer offlineMaxHours,
        @NotNull @Pattern(regexp = "GUARD|CLOSED", message = "После срока: GUARD или CLOSED") String offlineAfterExpiry,
        @NotNull Boolean offlineCheckShift,
        @NotNull @Pattern(regexp = "DENY", message = "Неизвестный телефон без связи — только отказ (DENY)") String offlineUnknownDevice) {

    static final int QR_MAX = 300;
    static final int GRACE_MAX = 240;
    static final int COOLDOWN_MAX = 600;
    static final int PRESENCE_MAX = 48;
    static final int FAILURES_MAX = 20;
    static final int WINDOW_MIN = 60;
    static final int WINDOW_MAX = 3600;
    static final int OFFLINE_MAX = 72;
}
