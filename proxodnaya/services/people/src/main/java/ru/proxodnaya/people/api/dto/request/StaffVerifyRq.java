package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Проверка кода из письма.
 *
 * @param email почта
 * @param code  6 цифр
 */
public record StaffVerifyRq(
        @NotBlank(message = "Введите почту") @Size(max = StaffCodeRq.MAX, message = "Слишком длинный адрес") String email,
        @NotBlank(message = "Введите код") @Pattern(regexp = "^\\s*\\d{3}[\\s-]?\\d{3}\\s*$", message = "Код — 6 цифр") String code) {
}
