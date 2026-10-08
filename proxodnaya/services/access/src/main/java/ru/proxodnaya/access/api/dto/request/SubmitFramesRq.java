package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Кадры челленджа живости: 3–5 JPEG base64.
 */
public record SubmitFramesRq(
        @NotEmpty @Size(min = 1, max = 10) List<String> frames
) {}
