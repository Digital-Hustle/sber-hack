package ru.documents.review.features.staff.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.features.staff.gateway.KeycloakAdminGateway;
import ru.documents.review.features.staff.gateway.StaffMailer;
import ru.documents.review.features.staff.model.StaffMember;
import ru.documents.review.features.staff.model.StaffRole;

/**
 * Сотрудники (FE-UI-04): вход только «почта + код из письма». Старший приглашает людей по почте и запрещает вход.
 */
@Service
public class StaffService {

    private static final Logger LOG = LoggerFactory.getLogger(StaffService.class);
    private static final int NAME_MAX = 64;
    private static final String VALIDATION = "VALIDATION_ERROR";

    private final KeycloakAdminGateway keycloak;
    private final StaffMailer mailer;
    private final Clock clock;

    public StaffService(KeycloakAdminGateway keycloak, StaffMailer mailer, Clock clock) {
        this.keycloak = keycloak;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Все сотрудники рабочего места, по фамилии. */
    public List<StaffMember> list() {
        List<StaffMember> out = new ArrayList<>();
        for (JsonNode u : keycloak.users()) {
            StaffRole role = StaffRole.of(keycloak.realmRoles(u.path("id").asText()));
            if (role != null) {
                out.add(member(u, role));
            }
        }
        out.sort(Comparator.comparing((StaffMember m) -> m.lastName().toLowerCase())
                .thenComparing(StaffMember::login));
        return out;
    }

    /**
     * Пригласить сотрудника: логин — почта, пароля нет; на почту уходит приглашение.
     *
     * @param email     почта
     * @param firstName имя
     * @param lastName  фамилия
     * @param role      роль
     * @param actor     кто приглашает
     * @return сотрудник
     */
    public StaffMember create(String email, String firstName, String lastName, StaffRole role, String actor) {
        String e = Emails.normalize(email);
        if (!Emails.valid(e)) {
            throw new DomainException(VALIDATION, 400, "Неверная почта", "Например: a.petrova@company.ru");
        }
        String first = name(firstName, "Имя");
        String last = name(lastName, "Фамилия");
        if (role == null) {
            throw new DomainException(VALIDATION, 400, "Не выбрана роль", "Оператор, старший или аудитор");
        }
        if (keycloak.findByEmail(e).isPresent()) {
            throw new DomainException("EMAIL_TAKEN", 409, "Сотрудник с такой почтой уже есть", "Проверьте адрес");
        }
        keycloak.create(e, first, last, e, role.realmRoles());
        LOG.info("staff.created role={} by={}", role, actor);
        mailer.invitation(e, first, role.title());
        return new StaffMember(e, e, first, last, role, true, clock.instant());
    }

    /**
     * Разрешить или запретить вход. Себя запретить нельзя — иначе можно остаться без старшего.
     *
     * @param login   логин
     * @param enabled разрешён ли вход
     * @param actor   кто меняет
     */
    public void setEnabled(String login, boolean enabled, String actor) {
        if (!enabled && login.equals(actor)) {
            throw new DomainException("SELF_DISABLE", 409, "Нельзя отключить себя", "Попросите другого старшего");
        }
        JsonNode u = keycloak.find(login).orElseThrow(StaffService::notFound);
        String id = u.path("id").asText();
        if (StaffRole.of(keycloak.realmRoles(id)) == null) {
            throw notFound();
        }
        keycloak.setEnabled(id, enabled);
        LOG.info("staff.enabled enabled={} by={}", enabled, actor);
    }

    private static StaffMember member(JsonNode u, StaffRole role) {
        return new StaffMember(u.path("username").asText(), u.path("email").asText(""), u.path("firstName").asText(""),
                u.path("lastName").asText(""), role, u.path("enabled").asBoolean(),
                u.hasNonNull("createdTimestamp") ? Instant.ofEpochMilli(u.path("createdTimestamp").asLong()) : null);
    }

    private static String name(String v, String what) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty() || s.length() > NAME_MAX) {
            throw new DomainException(VALIDATION, 400, what + ": от 1 до " + NAME_MAX + " символов", null);
        }
        return s;
    }

    private static DomainException notFound() {
        return new DomainException("STAFF_NOT_FOUND", 404, "Сотрудник не найден", null);
    }
}
