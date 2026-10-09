package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Продление сессии.
 *
 * @param refreshToken refresh из прошлого ответа
 */
public record StaffRefreshRq(
        @NotBlank(message = "Нет refresh-токена") @Size(max = MAX, message = "Слишком длинный токен") String refreshToken) {

    /** Длина токена. */
    public static final int MAX = 128;
}
