package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.documents.review.features.staff.model.StaffRole;

/**
 * Новый сотрудник.
 *
 * @param login     логин (латиница, 3–32)
 * @param firstName имя
 * @param lastName  фамилия
 * @param role      роль
 */
public record StaffRq(@NotBlank @Size(max = 32) String login, @NotBlank @Size(max = 64) String firstName,
                      @NotBlank @Size(max = 64) String lastName, @NotNull StaffRole role) {
}
