package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на создание попытки прохода: QR + направление.
 */
public record CreateAttemptRq(
        @NotBlank String qr,
        @NotNull String direction, // IN | OUT
        @NotBlank String checkpointId
) {}
