package ru.proxodnaya.people.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Новая проходная.
 *
 * @param zoneId зона, куда ведёт проходная
 * @param name   название
 */
public record CreateCheckpointRq(
        @NotNull(message = "Укажите зону") UUID zoneId,
        @NotBlank(message = "Укажите название") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String name) {
}
