package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Скан QR на киоске. Направление не передаётся — его выводит сервер (ADR-037).
 *
 * @param qr           строка QR {@code v1.<workerId>.<deviceId>.<window>.<sig>}
 * @param checkpointId проходная киоска; временно в теле, до подписи запросов киоска (ACC-12)
 */
public record ScanRq(
        @NotBlank @Size(max = ScanRq.MAX_QR) String qr,
        @NotBlank @Size(max = ScanRq.MAX_ID) String checkpointId
) {
    static final int MAX_QR = 1024;
    static final int MAX_ID = 64;
}
