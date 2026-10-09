package ru.proxodnaya.access.features.kiosk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Публичный ключ киоска для проверки подписи запросов (FR-64).
 * Киоск привязывается кодом сопряжения из админки (people: POST /auth/kiosk/pair).
 * Здесь — проекция ключа для проверки подписи.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KioskKey {

    private String kioskId;
    private String checkpointId;
    private byte[] publicKey;
    private String status;
}
