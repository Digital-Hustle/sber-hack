package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос кода входа.
 *
 * @param email рабочая почта
 */
public record StaffCodeRq(
        @NotBlank(message = "Введите почту") @Email(message = "Проверьте адрес почты") @Size(max = MAX, message = "Слишком длинный адрес")
        String email) {

    /** Длина адреса. */
    public static final int MAX = 254;
}
