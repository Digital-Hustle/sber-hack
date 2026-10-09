package ru.documents.review.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Правки полей документа.
 *
 * @param fields поля
 */
public record FieldsRq(@NotEmpty @Size(max = 50) List<@Valid Item> fields) {

    /**
     * Правка.
     *
     * @param name  поле
     * @param value значение (пусто — очистить)
     */
    public record Item(@NotBlank String name, @Size(max = 500) String value) {
    }
}
