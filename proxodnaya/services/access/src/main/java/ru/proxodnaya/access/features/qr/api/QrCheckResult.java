package ru.proxodnaya.access.features.qr.api;

import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Результат проверки QR.
 *
 * @param code     {@code OK} или причина отказа
 * @param workerId владелец QR (заполнен, если QR удалось разобрать)
 * @param deviceId устройство (заполнено, если QR удалось разобрать)
 * @param window   окно {@code floor(unix_s / 30)}
 */
public record QrCheckResult(ReasonCode code, String workerId, String deviceId, long window) {

    /**
     * Успешная проверка.
     *
     * @param workerId владелец
     * @param deviceId устройство
     * @param window   окно
     * @return результат с кодом {@code OK}
     */
    public static QrCheckResult ok(String workerId, String deviceId, long window) {
        return new QrCheckResult(ReasonCode.OK, workerId, deviceId, window);
    }

    /**
     * Отказ.
     *
     * @param code     причина
     * @param workerId владелец, если известен
     * @param deviceId устройство, если известно
     * @param window   окно, если известно
     * @return результат с кодом отказа
     */
    public static QrCheckResult fail(ReasonCode code, String workerId, String deviceId, long window) {
        return new QrCheckResult(code, workerId, deviceId, window);
    }

    /**
     * Проверка пройдена.
     *
     * @return {@code true}, если код {@code OK}
     */
    public boolean isOk() {
        return code == ReasonCode.OK;
    }
}
