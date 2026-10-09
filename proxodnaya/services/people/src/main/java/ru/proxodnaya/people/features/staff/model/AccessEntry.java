package ru.proxodnaya.people.features.staff.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись журнала доступа.
 *
 * @param id       id (UUIDv7)
 * @param ts       момент
 * @param byId     кто сделал (сотрудник) или {@code null}
 * @param byName   имя того, кто сделал
 * @param targetId над кем
 * @param target   имя или почта того, над кем
 * @param action   действие
 * @param fromRole роль до (ROLE)
 * @param toRole   роль после (INVITE, ROLE)
 * @param code     вид кода терминала (CODE)
 */
public record AccessEntry(UUID id, Instant ts, UUID byId, String byName, UUID targetId, String target, AccessAction action,
                          String fromRole, String toRole, String code) {
}
