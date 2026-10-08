package ru.proxodnaya.access.features.qr.api;

import java.time.Instant;

/**
 * Дешёвая локальная проверка QR пропуска: формат, ключ устройства (проекция), подпись, окно времени.
 * Сетевых вызовов нет — работает и тогда, когда people недоступен.
 */
public interface QrCheck {

    /**
     * Проверяет сырой QR, отсканированный киоском.
     *
     * @param raw строка {@code v1.<workerId>.<deviceId>.<window>.<sig>}
     * @param now текущее время сервера
     * @return результат: {@code OK} с владельцем QR или код отказа
     */
    QrCheckResult check(String raw, Instant now);
}
