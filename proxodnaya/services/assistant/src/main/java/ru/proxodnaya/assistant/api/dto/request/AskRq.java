package ru.proxodnaya.assistant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Вопрос помощнику.
 *
 * @param question вопрос по-русски
 */
public record AskRq(@NotBlank @Size(max = AskRq.MAX_LENGTH) String question) {

    /** Длина вопроса. */
    public static final int MAX_LENGTH = 1000;
}
