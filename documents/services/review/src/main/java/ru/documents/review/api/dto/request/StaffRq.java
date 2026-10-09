package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.documents.review.features.staff.model.StaffRole;

/**
 * Новый сотрудник.
 *
 * @param email     почта (она же логин)
 * @param firstName имя
 * @param lastName  фамилия
 * @param role      роль
 */
public record StaffRq(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 64) String firstName,
                      @NotBlank @Size(max = 64) String lastName, @NotNull StaffRole role) {
}
