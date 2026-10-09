package ru.proxodnaya.people.api.dto.response;

import java.util.UUID;

/**
 * Запись журнала доступа.
 *
 * @param id       id
 * @param ts       момент, Unix ms
 * @param by       кто (имя)
 * @param byId     кто (id) или {@code null}
 * @param target   над кем (имя)
 * @param targetId над кем (id)
 * @param action   INVITE | ROLE | DISABLE | ENABLE | JOIN | CODE | LOGIN | LOGOUT
 * @param from     роль до
 * @param to       роль после
 * @param code     вид кода терминала (CODE)
 */
public record AccessEntryRs(UUID id, long ts, String by, UUID byId, String target, UUID targetId, String action, String from,
                            String to, String code) {
}
