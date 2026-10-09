package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Снимок для проверки до сохранения.
 *
 * @param photo JPEG base64 (или data-URL)
 */
public record CheckFaceRq(
        @NotBlank(message = "Нужен снимок") @Size(max = Limits.PHOTO, message = "Снимок слишком большой") String photo) {
}
