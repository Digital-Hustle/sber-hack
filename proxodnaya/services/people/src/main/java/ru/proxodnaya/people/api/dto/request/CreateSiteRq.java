package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Новый объект.
 *
 * @param name    название
 * @param address адрес
 */
public record CreateSiteRq(
        @NotBlank(message = "Укажите название") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String name,
        @Size(max = Limits.ADDRESS, message = "Не длиннее 300 символов") String address) {
}
