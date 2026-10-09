package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Изменение локального кода терминала.
 *
 * @param code 4–6 цифр; слабые сочетания проверяет сервер
 */
public record SetTerminalCodeRq(@NotBlank @Pattern(regexp = "[0-9]{4,6}") String code) {
}
