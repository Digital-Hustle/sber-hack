package ru.proxodnaya.people.api.dto.request;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Новый рабочий.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param zoneIds    зоны допуска (можно не указывать)
 * @param permits    допуски со сроком (без них проход закрыт: PERMIT_EXPIRED)
 */
public record CreateWorkerRq(
        @NotBlank(message = "Укажите ФИО") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String fullName,
        @NotBlank(message = "Укажите должность") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String position,
        @NotBlank(message = "Укажите подрядчика") @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") String contractor,
        @Size(max = Limits.ITEMS, message = "Слишком много зон") Set<UUID> zoneIds,
        @Valid @Size(max = Limits.ITEMS, message = "Слишком много допусков") List<PermitRq> permits) {
}
