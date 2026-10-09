package ru.proxodnaya.people.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Новая зона.
 *
 * @param siteId   объект
 * @param name     название
 * @param capacity вместимость (пусто — без ограничения)
 */
public record CreateZoneRq(
        @NotNull(message = "Укажите объект") UUID siteId,
        @NotBlank(message = "Укажите название") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String name,
        @Positive(message = "Вместимость больше нуля") Integer capacity) {
}
