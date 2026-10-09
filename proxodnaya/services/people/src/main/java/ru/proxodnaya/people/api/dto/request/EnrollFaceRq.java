package ru.proxodnaya.people.api.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.proxodnaya.people.shared.Limits;

/**
 * Снимки для эталона лица.
 *
 * @param frames 1..3 снимка JPEG base64 (или data-URL), лицо анфас
 */
public record EnrollFaceRq(
        @NotEmpty(message = "Нужен хотя бы один снимок") @Size(max = Limits.FACE_FRAMES, message = "Не больше трёх снимков")
        List<@NotBlank @Size(max = Limits.PHOTO, message = "Снимок слишком большой") String> frames) {
}
