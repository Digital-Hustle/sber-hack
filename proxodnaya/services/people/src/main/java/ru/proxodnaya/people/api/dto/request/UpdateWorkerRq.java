package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Изменение карточки: пустое поле ({@code null}) не меняется, пустая строка — ошибка.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 */
public record UpdateWorkerRq(
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String fullName,
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String position,
        @Size(max = Limits.TEXT, message = "Не длиннее 200 символов") @Pattern(regexp = NOT_BLANK, message = "Не может быть пустым")
        String contractor) {

    /** Хотя бы один непробельный символ. */
    public static final String NOT_BLANK = "(?s).*\\S.*";
}
