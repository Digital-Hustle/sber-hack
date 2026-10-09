package ru.documents.review.features.staff.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.documents.common.exception.DomainException;
import ru.documents.common.security.ServiceTokenProvider;

/**
 * Admin REST Keycloak (realm documents) от имени svc-review: у него только manage-users / view-users / query-users.
 * Код из письма хранится в Keycloak как OTP-учётные данные (TOTP с шагом = сроку кода), проверяет его Keycloak.
 */
@Component
public class KeycloakAdminGateway {

    private static final String AUTH = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String USER = "/users/{id}";
    private static final String OTP = "otp";
    private static final int PAGE = 500;

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public KeycloakAdminGateway(@Qualifier("keycloakAdminRestClient") RestClient http, ServiceTokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /**
     * Все пользователи realm (service accounts Keycloak сюда не попадают).
     *
     * @return представления пользователей
     */
    public List<JsonNode> users() {
        JsonNode list = call(() -> http.get().uri("/users?briefRepresentation=false&max={m}", PAGE)
                .header(AUTH, bearer()).retrieve().body(JsonNode.class));
        List<JsonNode> out = new ArrayList<>();
        if (list != null) {
            list.forEach(out::add);
        }
        return out;
    }

    /**
     * Пользователь по логину.
     *
     * @param login логин
     * @return пользователь или пусто
     */
    public Optional<JsonNode> find(String login) {
        JsonNode list = call(() -> http.get().uri("/users?exact=true&briefRepresentation=false&username={u}", login)
                .header(AUTH, bearer()).retrieve().body(JsonNode.class));
        return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Пользователь по почте.
     *
     * @param email почта (в нижнем регистре)
     * @return пользователь или пусто
     */
    public Optional<JsonNode> findByEmail(String email) {
        JsonNode list = call(() -> http.get().uri("/users?exact=true&briefRepresentation=false&email={e}", email)
                .header(AUTH, bearer()).retrieve().body(JsonNode.class));
        if (list == null) {
            return Optional.empty();
        }
        for (JsonNode u : list) {
            if (email.equalsIgnoreCase(u.path("email").asText())) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    /**
     * Realm-роли пользователя.
     *
     * @param id идентификатор Keycloak
     * @return имена ролей
     */
    public List<String> realmRoles(String id) {
        JsonNode list = call(() -> http.get().uri(USER + "/role-mappings/realm", id).header(AUTH, bearer())
                .retrieve().body(JsonNode.class));
        List<String> out = new ArrayList<>();
        if (list != null) {
            list.forEach(r -> out.add(r.path("name").asText()));
        }
        return out;
    }

    /**
     * Когда выдан текущий код (создан OTP).
     *
     * @param id идентификатор Keycloak
     * @return время или пусто, если кода нет
     */
    public Optional<Instant> codeIssuedAt(String id) {
        return otps(id).stream().filter(c -> c.hasNonNull("createdDate"))
                .map(c -> Instant.ofEpochMilli(c.path("createdDate").asLong())).max(Instant::compareTo);
    }

    /**
     * Создать сотрудника: пароля нет, код появится при первом запросе входа.
     *
     * @param login     логин (почта)
     * @param firstName имя
     * @param lastName  фамилия
     * @param email     почта
     * @param roles     realm-роли
     */
    public void create(String login, String firstName, String lastName, String email, List<String> roles) {
        try {
            http.post().uri("/users").header(AUTH, bearer()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", login, "firstName", firstName, "lastName", lastName,
                            "email", email, "emailVerified", true, "enabled", true))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new DomainException("EMAIL_TAKEN", 409, "Сотрудник с такой почтой уже есть", "Проверьте адрес");
        } catch (RestClientException e) {
            throw unavailable();
        }
        String id = find(login).orElseThrow(KeycloakAdminGateway::unavailable).path("id").asText();
        // роли берём из «доступных для назначения»: чтение /roles требует view-realm, которого у svc-review нет
        JsonNode available = call(() -> http.get().uri(USER + "/role-mappings/realm/available", id)
                .header(AUTH, bearer()).retrieve().body(JsonNode.class));
        List<JsonNode> reps = new ArrayList<>();
        if (available != null) {
            available.forEach(r -> {
                if (roles.contains(r.path("name").asText())) {
                    reps.add(r);
                }
            });
        }
        call(() -> http.post().uri(USER + "/role-mappings/realm", id).header(AUTH, bearer())
                .contentType(MediaType.APPLICATION_JSON).body(reps).retrieve().toBodilessEntity());
    }

    /**
     * Выдать новый код: прежний перестаёт действовать, открытые сессии не трогаем.
     *
     * @param id         идентификатор Keycloak
     * @param secret     новый секрет
     * @param periodSec  шаг TOTP, секунды (= срок кода)
     */
    public void issueCode(String id, String secret, long periodSec) {
        for (JsonNode c : otps(id)) {
            call(() -> http.delete().uri(USER + "/credentials/{c}", id, c.path("id").asText()).header(AUTH, bearer())
                    .retrieve().toBodilessEntity());
        }
        call(() -> http.put().uri(USER, id).header(AUTH, bearer()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("credentials", List.of(credential(secret, periodSec)), "requiredActions", List.of()))
                .retrieve().toBodilessEntity());
    }

    /**
     * Разрешить или запретить вход; при запрете — выход со всех устройств.
     *
     * @param id      идентификатор Keycloak
     * @param enabled разрешён ли вход
     */
    public void setEnabled(String id, boolean enabled) {
        call(() -> http.put().uri(USER, id).header(AUTH, bearer()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled)).retrieve().toBodilessEntity());
        if (!enabled) {
            logout(id);
        }
    }

    private void logout(String id) {
        call(() -> http.post().uri(USER + "/logout", id).header(AUTH, bearer()).retrieve().toBodilessEntity());
    }

    private List<JsonNode> otps(String id) {
        JsonNode list = call(() -> http.get().uri(USER + "/credentials", id).header(AUTH, bearer()).retrieve()
                .body(JsonNode.class));
        List<JsonNode> out = new ArrayList<>();
        if (list != null) {
            list.forEach(c -> {
                if (OTP.equals(c.path("type").asText())) {
                    out.add(c);
                }
            });
        }
        return out;
    }

    private static Map<String, Object> credential(String secret, long periodSec) {
        return Map.of("type", OTP, "userLabel", "Код из письма",
                "secretData", "{\"value\":\"" + secret + "\"}",
                "credentialData", "{\"subType\":\"totp\",\"digits\":6,\"period\":" + periodSec
                        + ",\"algorithm\":\"HmacSHA1\",\"counter\":0}");
    }

    private String bearer() {
        return BEARER + tokens.token();
    }

    private static <T> T call(Supplier<T> s) {
        try {
            return s.get();
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException("STAFF_UNAVAILABLE", 503, "Служба учётных записей недоступна",
                "Повторите через минуту");
    }
}
