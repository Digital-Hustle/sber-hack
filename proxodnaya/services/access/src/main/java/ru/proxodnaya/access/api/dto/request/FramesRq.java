package ru.proxodnaya.access.api.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Кадры челленджа.
 *
 * @param frames JPEG base64, 1–10 штук
 */
public record FramesRq(@NotEmpty @Size(max = FramesRq.MAX_FRAMES) List<@NotBlank String> frames) {
    static final int MAX_FRAMES = 10;
}
