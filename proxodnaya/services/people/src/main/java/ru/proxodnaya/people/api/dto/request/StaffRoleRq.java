package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotNull;
import ru.proxodnaya.people.features.staff.model.StaffRole;

/**
 * Смена роли.
 *
 * @param role новая роль
 */
public record StaffRoleRq(@NotNull(message = "Выберите роль") StaffRole role) {
}
