package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.shared.Limits;

/**
 * Приглашение сотрудника.
 *
 * @param name  имя
 * @param email почта
 * @param role  роль
 */
public record InviteStaffRq(
        @NotBlank(message = "Введите имя") @Size(max = Limits.TEXT, message = "Слишком длинное имя") String name,
        @NotBlank(message = "Введите почту") @Email(message = "Проверьте адрес почты")
        @Size(max = StaffCodeRq.MAX, message = "Слишком длинный адрес") String email,
        @NotNull(message = "Выберите роль") StaffRole role) {
}
