package ru.proxodnaya.access.config.properties;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки прохода (application.yml, префикс {@code access}).
 *
 * @param qrTolerance        допуск окна QR (часы телефона), по умолчанию 90 с
 * @param repeatScanCooldown пауза после успешного прохода, раньше — {@code REPEAT_SCAN}
 * @param presenceTtl        сколько считаем человека внутри после входа (дальше скан = снова вход)
 * @param challengeTtl       сколько ждём кадры челленджа
 * @param framesRequired     сколько кадров просим у киоска
 * @param tempLockFailures   неудачных сверок лица до {@code TEMP_LOCKED}
 * @param tempLockWindow     окно подсчёта неудачных сверок
 * @param siteZone           часовой пояс объекта (дата допуска)
 * @param deps               адреса и таймаут зависимостей
 */
@ConfigurationProperties("access")
public record AccessProperties(
        Duration qrTolerance,
        Duration repeatScanCooldown,
        Duration presenceTtl,
        Duration challengeTtl,
        int framesRequired,
        int tempLockFailures,
        Duration tempLockWindow,
        ZoneId siteZone,
        Deps deps
) {

    /**
     * Синхронные зависимости пути прохода.
     *
     * @param faceUrl   базовый URL face
     * @param peopleUrl базовый URL people
     * @param shiftUrl  базовый URL shift
     * @param timeout   общий таймаут вызова (fail-closed)
     */
    public record Deps(String faceUrl, String peopleUrl, String shiftUrl, Duration timeout) {
    }
}
