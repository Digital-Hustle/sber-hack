package ru.proxodnaya.people.api.mapper;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import ru.proxodnaya.people.api.dto.response.AccessEntryRs;
import ru.proxodnaya.people.api.dto.response.AccessLogPageRs;
import ru.proxodnaya.people.api.dto.response.CodeSentRs;
import ru.proxodnaya.people.api.dto.response.PageRs;
import ru.proxodnaya.people.api.dto.response.StaffPageRs;
import ru.proxodnaya.people.api.dto.response.StaffSessionRs;
import ru.proxodnaya.people.api.dto.response.StaffUserRs;
import ru.proxodnaya.people.features.staff.model.AccessEntry;
import ru.proxodnaya.people.features.staff.model.Actor;
import ru.proxodnaya.people.features.staff.model.CodeSent;
import ru.proxodnaya.people.features.staff.model.StaffPage;
import ru.proxodnaya.people.features.staff.model.StaffSession;
import ru.proxodnaya.people.features.staff.model.StaffUser;

/**
 * Модели персонала → DTO; токен → актор.
 */
@Component
public class StaffApiMapper {

    /**
     * Сотрудник.
     *
     * @param user модель
     * @return DTO
     */
    public StaffUserRs toRs(StaffUser user) {
        return new StaffUserRs(user.id(), user.name(), user.email(), user.role().name(), user.status().name(),
                user.lastSeen() == null ? null : user.lastSeen().toEpochMilli());
    }

    /**
     * Код отправлен.
     *
     * @param sent модель
     * @return DTO
     */
    public CodeSentRs toRs(CodeSent sent) {
        return new CodeSentRs(sent.sentTo(), sent.expiresAt(), sent.resendAt());
    }

    /**
     * Сессия.
     *
     * @param session модель
     * @return DTO
     */
    public StaffSessionRs toRs(StaffSession session) {
        return new StaffSessionRs(session.accessToken(), session.refreshToken(), "Bearer", session.expiresAt(),
                session.sessionExpiresAt(), toRs(session.user()));
    }

    /**
     * Страница персонала.
     *
     * @param page модель
     * @return DTO
     */
    public StaffPageRs toStaffPage(StaffPage<StaffUser> page) {
        return new StaffPageRs(page.items().stream().map(this::toRs).toList(), new PageRs(page.next(), page.total()));
    }

    /**
     * Страница журнала.
     *
     * @param page модель
     * @return DTO
     */
    public AccessLogPageRs toLogPage(StaffPage<AccessEntry> page) {
        return new AccessLogPageRs(page.items().stream()
                .map(entry -> new AccessEntryRs(entry.id(), entry.ts().toEpochMilli(), entry.byName(), entry.byId(), entry.target(),
                        entry.targetId(), entry.action().name(), entry.fromRole(), entry.toRole(), entry.code()))
                .toList(), new PageRs(page.next(), page.total()));
    }

    /**
     * Кто действует: {@code sub} и {@code name} токена.
     *
     * @param token JWT
     * @return актор
     */
    public Actor actor(Jwt token) {
        String name = token.getClaimAsString("name");
        return new Actor(uuid(token.getSubject()), name == null ? token.getClaimAsString("preferred_username") : name);
    }

    /**
     * UUID из строки или {@code null}.
     *
     * @param value строка
     * @return UUID
     */
    public static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
