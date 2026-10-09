package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ручной проход охранника (FR-14). Направление не передаётся — его выводит сервер по присутствию (ADR-037);
 * охранник — из токена, а не из тела.
 *
 * @param workerId     кого пропускают
 * @param checkpointId проходная киоска; временно в теле, до подписи запросов киоска (ACC-12)
 * @param note         причина ручного прохода, обязательна
 */
public record ManualPassRq(
        @NotBlank @Size(max = ManualPassRq.MAX_ID) String workerId,
        @NotBlank @Size(max = ManualPassRq.MAX_ID) String checkpointId,
        @NotBlank @Size(min = ManualPassRq.MIN_NOTE, max = ManualPassRq.MAX_NOTE) String note
) {
    static final int MAX_ID = 64;
    static final int MIN_NOTE = 3;
    static final int MAX_NOTE = 500;
}
