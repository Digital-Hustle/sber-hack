package ru.documents.review.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Запрос соискателю «дослать/переснять».
 *
 * @param items пункты
 */
public record ResubmitRq(@NotEmpty @Size(max = 20) List<@Valid RequestItem> items) {

    /**
     * Пункт.
     *
     * @param documentId документ (переснять)
     * @param type       вид (дослать)
     * @param reason     причина для соискателя
     */
    public record RequestItem(UUID documentId, String type, @NotBlank @Size(max = 500) String reason) {
    }
}
