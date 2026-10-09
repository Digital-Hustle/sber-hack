package ru.proxodnaya.access.features.settings.api;

import java.time.Duration;

/**
 * Параметры прохода, которые меняются из кабинета без перезапуска (ACC-18). Значения по умолчанию — из env.
 */
public interface PassTuning {

    /**
     * Допуск часов телефона для окна QR.
     *
     * @return допуск
     */
    Duration qrTolerance();

    /**
     * Пауза после успешного прохода (раньше — REPEAT_SCAN).
     *
     * @return пауза
     */
    Duration repeatScanCooldown();

    /**
     * Сколько считаем человека внутри после входа.
     *
     * @return срок
     */
    Duration presenceTtl();

    /**
     * Неудачных сверок лица до TEMP_LOCKED.
     *
     * @return число
     */
    int tempLockFailures();

    /**
     * Окно подсчёта неудачных сверок.
     *
     * @return окно
     */
    Duration tempLockWindow();

    /**
     * Минимальная похожесть лица; ниже порога face (FACE_MATCH_THRESHOLD) опустить нельзя — face сам ответит match=false.
     *
     * @return порог
     */
    double faceThreshold();

    /**
     * Пускать на вход только по смене.
     *
     * @return требовать смену
     */
    boolean requireShift();
}
