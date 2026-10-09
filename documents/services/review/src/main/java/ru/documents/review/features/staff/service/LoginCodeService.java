package ru.documents.review.features.staff.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.config.StaffProperties;
import ru.documents.review.features.staff.gateway.KeycloakAdminGateway;
import ru.documents.review.features.staff.gateway.StaffMailer;
import ru.documents.review.features.staff.model.AuthMode;
import ru.documents.review.features.staff.model.CodeSent;
import ru.documents.review.features.staff.model.StaffMember;
import ru.documents.review.features.staff.model.StaffRole;

/**
 * Вход по коду из письма (FE-UI-04, ADR-021). На каждый запрос — новый секрет TOTP с шагом = сроку кода
 * в Keycloak и письмо с текущим кодом; проверяет код Keycloak (поток documents-code-direct, защита от перебора).
 * Ответ одинаковый для любой почты, не чаще одного письма за {@code resendAfter} (по времени выдачи кода в Keycloak).
 */
@Service
public class LoginCodeService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginCodeService.class);
    private static final long MINUTE = 60;

    private final KeycloakAdminGateway keycloak;
    private final StaffMailer mailer;
    private final StaffProperties props;
    private final Clock clock;
    private final StaffService staff;

    public LoginCodeService(KeycloakAdminGateway keycloak, StaffMailer mailer, StaffProperties props, Clock clock,
                            StaffService staff) {
        this.keycloak = keycloak;
        this.staff = staff;
        this.mailer = mailer;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Отправить код, если почта принадлежит сотруднику с разрешённым входом.
     *
     * @param email почта
     * @return куда отправили и сроки (одинаково для любой почты)
     */
    public CodeSent request(String email) {
        String e = Emails.normalize(email);
        if (!Emails.valid(e)) {
            throw new DomainException("VALIDATION_ERROR", 400, "Неверная почта", "Например: name@company.ru");
        }
        Instant now = clock.instant();
        long ttl = props.codeTtl().toSeconds();
        Optional<JsonNode> user = keycloak.findByEmail(e);
        if (user.isPresent() && user.get().path("enabled").asBoolean()) {
            String id = user.get().path("id").asText();
            if (StaffRole.of(keycloak.realmRoles(id)) != null && due(id, now)) {
                String secret = Totp.newSecret();
                keycloak.issueCode(id, secret, ttl);
                mailer.loginCode(e, Totp.code(secret, now.getEpochSecond(), ttl), ttl / MINUTE);
                LOG.info("staff.code-sent user={}", user.get().path("username").asText());
            }
        }
        return new CodeSent(Emails.mask(e), ttl, props.resendAfter().toSeconds());
    }

    /**
     * Тестовый режим: выдать код сразу в ответе (форма подставляет его вместо 000000 и быстрого входа).
     * В боевом режиме ручки нет (404), проверяет код всё равно Keycloak.
     *
     * @param email почта сотрудника
     * @return действующий код
     */
    public String testCode(String email) {
        if (!props.testMode()) {
            throw new DomainException("TEST_MODE_OFF", 404, "Тестовый вход выключен", "Войдите кодом из письма");
        }
        String e = Emails.normalize(email);
        JsonNode u = keycloak.findByEmail(e).filter(n -> n.path("enabled").asBoolean())
                .orElseThrow(() -> new DomainException("STAFF_NOT_FOUND", 404, "Сотрудник не найден", null));
        String id = u.path("id").asText();
        if (StaffRole.of(keycloak.realmRoles(id)) == null) {
            throw new DomainException("STAFF_NOT_FOUND", 404, "Сотрудник не найден", null);
        }
        long ttl = props.codeTtl().toSeconds();
        String secret = Totp.newSecret();
        keycloak.issueCode(id, secret, ttl);
        LOG.warn("staff.test-code user={} (APP_MODE=dev)", u.path("username").asText());
        return Totp.code(secret, clock.instant().getEpochSecond(), ttl);
    }

    /**
     * Режим стенда для формы входа.
     *
     * @return режим и тестовые сотрудники (только в dev)
     */
    public AuthMode mode() {
        if (!props.testMode()) {
            return new AuthMode("prod", List.of());
        }
        List<AuthMode.TestUser> users = staff.list().stream().filter(StaffMember::enabled)
                .filter(m -> !m.email().isBlank())
                .map(m -> new AuthMode.TestUser(m.email(), (m.firstName() + " " + m.lastName()).trim(), m.role()))
                .toList();
        return new AuthMode("dev", users);
    }

    private boolean due(String id, Instant now) {
        return keycloak.codeIssuedAt(id).map(t -> !t.plus(props.resendAfter()).isAfter(now)).orElse(true);
    }
}
