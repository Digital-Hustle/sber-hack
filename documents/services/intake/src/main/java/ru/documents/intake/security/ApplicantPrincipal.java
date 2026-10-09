package ru.documents.intake.security;

import java.util.UUID;

/**
 * Соискатель, вошедший по ключу заявки: видит только эту заявку.
 *
 * @param applicationId заявка
 */
public record ApplicantPrincipal(UUID applicationId) {
}
