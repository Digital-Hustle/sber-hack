package ru.proxodnaya.access.features.kiosk.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Терминал проходной.
 *
 * @param id            id терминала (= id его ключа)
 * @param publicKey     публичный ключ, SPKI base64url
 * @param pairCode      код сопряжения, пока терминал не привязан
 * @param name          название в кабинете
 * @param checkpointId  проходная
 * @param mode          своя логика прохода ({@code QR_FACE|FACE_FIRST|QR_ONLY}) или {@code null} — общая
 * @param offlinePolicy своя политика без связи ({@code GUARD|CLOSED|LOCAL}) или {@code null} — общая
 * @param createdAt     регистрация
 * @param pairedAt      сопряжение или {@code null}
 * @param lastSeen      последний подписанный запрос
 */
public record Kiosk(UUID id, String publicKey, String pairCode, String name, String checkpointId, String mode,
                    String offlinePolicy, Instant createdAt, Instant pairedAt, Instant lastSeen) {

    /**
     * Привязан ли терминал к проходной.
     *
     * @return {@code true}, если сопряжён
     */
    public boolean paired() {
        return pairedAt != null && checkpointId != null;
    }
}
