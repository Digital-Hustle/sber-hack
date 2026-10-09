package ru.proxodnaya.people.api.dto.request;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Изменение карточки: пустое поле ({@code null}) не меняется, пустая строка — ошибка; списки заменяются целиком.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param zoneIds    зоны допуска
 * @param permits    допуски со сроком
 */
public record UpdateWorkerRq(
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String fullName,
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String position,
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String contractor,
        @Size(max = Limits.ITEMS, message = "Слишком много зон") Set<UUID> zoneIds,
        @Valid @Size(max = Limits.ITEMS, message = "Слишком много допусков") List<PermitRq> permits) {

    /** Хотя бы один непробельный символ. */
    public static final String NOT_BLANK = "(?s).*\\S.*";
}
