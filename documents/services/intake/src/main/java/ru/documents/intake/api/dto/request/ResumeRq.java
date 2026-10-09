package ru.documents.intake.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Продолжение заявки с другого устройства.
 *
 * @param phoneOrEmail контакт, указанный при создании
 * @param resumeCode   код возврата
 */
public record ResumeRq(@NotBlank String phoneOrEmail, @NotBlank String resumeCode) {
}
