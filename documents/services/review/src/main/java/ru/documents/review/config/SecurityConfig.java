package ru.documents.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.documents.common.security.KeycloakJwt;

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
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // EventSource не передаёт заголовки: доступ по одноразовому билету из /stream/ticket.
                        .requestMatchers(HttpMethod.GET, "/api/v1/stream").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications", "/api/v1/applications/*/history",
                                "/api/v1/metrics").hasAnyRole(OPERATOR, SENIOR, AUDITOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents/*/reprocess").hasRole(SENIOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/export", "/api/v1/reports/expiring/export")
                        .hasRole(SENIOR)
                        .requestMatchers("/api/v1/**").hasAnyRole(OPERATOR, SENIOR)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(KeycloakJwt.converter())));
        return http.build();
    }

    /** Проверка JWT Keycloak. */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks, @Value("${app.oidc.issuer}") String iss) {
        return KeycloakJwt.decoder(jwks, iss);
    }
}
