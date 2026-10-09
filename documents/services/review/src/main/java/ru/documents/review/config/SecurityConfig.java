package ru.documents.review.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import ru.documents.common.security.KeycloakJwt;
import ru.documents.review.features.workplace.command.service.TicketService;

/**
 * Матрица доступа (FR-50, B-07): аудитор — только очередь (маскированная), история и метрики.
 */
@Configuration
public class SecurityConfig {

    private static final String OPERATOR = "OPERATOR";
    private static final String SENIOR = "SENIOR_OPERATOR";
    private static final String AUDITOR = "AUDITOR";

    /** Цепочка фильтров. */
    @Bean
    public SecurityFilterChain chain(HttpSecurity http, TicketService tickets) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // EventSource не передаёт заголовки: доступ по одноразовому билету (TicketFilter).
                        .requestMatchers(HttpMethod.GET, "/api/v1/stream").hasRole(TicketFilter.ROLE)
                        // запрос кода входа — до входа; сам код проверяет Keycloak, частоту — шлюз и StaffMailer
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/code", "/api/v1/auth/test-code").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/mode").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications", "/api/v1/applications/*/history",
                                "/api/v1/metrics").hasAnyRole(OPERATOR, SENIOR, AUDITOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents/*/reprocess").hasRole(SENIOR)
                        .requestMatchers("/api/v1/staff", "/api/v1/staff/**").hasRole(SENIOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/export", "/api/v1/reports/expiring/export")
                        .hasRole(SENIOR)
                        .requestMatchers("/api/v1/**").hasAnyRole(OPERATOR, SENIOR)
                        .anyRequest().denyAll())
                .addFilterBefore(new TicketFilter(tickets), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(KeycloakJwt.converter())));
        return http.build();
    }

    /** Health и контракт OpenAPI — мимо цепочки безопасности (без данных). */
    @Bean
    public WebSecurityCustomizer publicEndpoints() {
        return web -> web.ignoring().requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus",
                "/v3/api-docs/**");
    }

    /** Проверка JWT Keycloak: подпись, issuer, aud ({@code documents-api} оператора или имя этого сервиса). */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks, @Value("${app.oidc.issuer}") String iss,
                                 @Value("${app.oidc.audiences:documents-api,review}") List<String> audiences) {
        return KeycloakJwt.decoder(jwks, iss, audiences);
    }
}
