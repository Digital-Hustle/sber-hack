package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Запрос challenge для device-токена.
 *
 * @param deviceId id устройства из активации
 */
public record DeviceChallengeRq(
        @NotBlank(message = "Нет id устройства")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "id устройства: 8–64 символа A-Z, a-z, 0-9, _ или -")
        String deviceId) {
}
