package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Продление сессии.
 *
 * @param refreshToken refresh из прошлого ответа; без него — из httpOnly-cookie (PPL-14)
 */
public record StaffRefreshRq(
        @Size(max = MAX, message = "Слишком длинный токен") String refreshToken) {

    /** Длина токена. */
    public static final int MAX = 128;
}
