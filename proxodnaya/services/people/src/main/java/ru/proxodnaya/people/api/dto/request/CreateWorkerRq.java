package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Новый рабочий.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 */
public record CreateWorkerRq(
        @NotBlank(message = "Укажите ФИО") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String fullName,
        @NotBlank(message = "Укажите должность") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String position,
        @NotBlank(message = "Укажите подрядчика") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String contractor) {
}
