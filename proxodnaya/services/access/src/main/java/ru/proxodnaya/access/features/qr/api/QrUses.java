package ru.proxodnaya.access.features.qr.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Атомарное гашение пары {@code deviceId|window}. Вызывается только внутри транзакции решения о проходе.
 */
public interface QrUses {

    /**
     * Гасит QR.
     *
     * @param deviceId  устройство
     * @param window    окно QR
     * @param attemptId попытка, которая гасит
     * @param moment        момент гашения
     * @return {@code true}, если погасили сейчас; {@code false}, если QR уже был погашен ({@code QR_REUSED})
     */
    boolean consume(String deviceId, long window, UUID attemptId, Instant moment);
}
