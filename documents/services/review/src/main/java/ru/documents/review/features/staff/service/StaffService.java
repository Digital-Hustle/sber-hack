package ru.documents.review.features.staff.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.config.StaffProperties;
import ru.documents.review.features.staff.gateway.KeycloakAdminGateway;
import ru.documents.review.features.staff.model.Enrollment;
import ru.documents.review.features.staff.model.StaffMember;
import ru.documents.review.features.staff.model.StaffRole;

/**
 * Сотрудники (FE-UI-03): вход только «логин + код из приложения». Старший заводит людей и выдаёт коды;
 * секрет показывается один раз и нигде у нас не хранится.
 */
@Service
public class StaffService {

    private static final Logger LOG = LoggerFactory.getLogger(StaffService.class);
    private static final Pattern LOGIN = Pattern.compile("^[a-z][a-z0-9._-]{2,31}$");
    private static final int NAME_MAX = 64;
    private static final String VALIDATION = "VALIDATION_ERROR";

    private final KeycloakAdminGateway keycloak;
    private final StaffProperties props;

    public StaffService(KeycloakAdminGateway keycloak, StaffProperties props) {
        this.keycloak = keycloak;
        this.props = props;
    }

    /** Все сотрудники рабочего места, по фамилии. */
    public List<StaffMember> list() {
        List<StaffMember> out = new ArrayList<>();
        for (JsonNode u : keycloak.users()) {
            String id = u.path("id").asText();
            StaffRole role = StaffRole.of(keycloak.realmRoles(id));
            if (role != null) {
                out.add(new StaffMember(u.path("username").asText(), u.path("firstName").asText(""),
                        u.path("lastName").asText(""), role, u.path("enabled").asBoolean(), keycloak.hasCode(id),
                        u.hasNonNull("createdTimestamp") ? Instant.ofEpochMilli(u.path("createdTimestamp").asLong())
                                : null));
            }
        }
        out.sort(Comparator.comparing((StaffMember m) -> m.lastName().toLowerCase())
                .thenComparing(StaffMember::login));
        return out;
    }

    /**
     * Завести сотрудника и сразу выдать код.
     *
     * @param login     логин
     * @param firstName имя
     * @param lastName  фамилия
     * @param role      роль
     * @param actor     кто заводит
     * @return код для QR (один раз)
     */
    public Enrollment create(String login, String firstName, String lastName, StaffRole role, String actor) {
        String l = login == null ? "" : login.trim().toLowerCase();
        if (!LOGIN.matcher(l).matches()) {
            throw new DomainException(VALIDATION, 400, "Логин: 3–32 символа — латиница, цифры, точка, дефис",
                    "Например: ivanov или a.petrova");
        }
        String first = name(firstName, "Имя");
        String last = name(lastName, "Фамилия");
        if (role == null) {
            throw new DomainException(VALIDATION, 400, "Не выбрана роль", "Оператор, старший или аудитор");
        }
        String secret = Totp.newSecret();
        keycloak.create(l, first, last, secret, role.realmRoles());
        LOG.info("staff.created login={} role={} by={}", l, role, actor);
        return enrollment(l, secret);
    }

    /**
     * Новый код (старый перестаёт работать, сессии закрываются).
     *
     * @param login логин
     * @param actor кто выдаёт
     * @return код для QR (один раз)
     */
    public Enrollment reissue(String login, String actor) {
        String id = staffId(login);
        String secret = Totp.newSecret();
        keycloak.replaceCode(id, secret);
        LOG.info("staff.code-reissued login={} by={}", login, actor);
        return enrollment(login, secret);
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
        keycloak.setEnabled(staffId(login), enabled);
        LOG.info("staff.enabled login={} enabled={} by={}", login, enabled, actor);
    }

    private String staffId(String login) {
        JsonNode u = keycloak.find(login).orElseThrow(StaffService::notFound);
        String id = u.path("id").asText();
        if (StaffRole.of(keycloak.realmRoles(id)) == null) {
            throw notFound();
        }
        return id;
    }

    private Enrollment enrollment(String login, String secret) {
        return new Enrollment(login, Totp.uri(props.issuer(), login, secret), Totp.base32(secret));
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
