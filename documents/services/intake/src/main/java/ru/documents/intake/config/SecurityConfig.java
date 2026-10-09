package ru.documents.intake.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import ru.documents.common.security.KeycloakJwt;
import ru.documents.intake.security.ApplicantKeyFilter;

/**
 * Доступ (Д8): соискатель — по ключу заявки, оператор — JWT Keycloak с ролью, внутренние — service account.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String OPERATOR = "OPERATOR";
    private static final String SENIOR = "SENIOR_OPERATOR";

    /** Цепочка фильтров. */
    @Bean
    public SecurityFilterChain chain(HttpSecurity http, JdbcClient jdbc, IntakeProperties props) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.cacheControl(c -> { }))
                .addFilterBefore(new ApplicantKeyFilter(jdbc, props.pepper()), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applicant/questionnaire").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/applicant/applications", "/api/v1/applicant/resume")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applicant/applications/*/stream").permitAll()
                        .requestMatchers("/api/v1/applicant/**").hasRole("APPLICANT")
                        .requestMatchers("/api/v1/files/**").hasAnyRole("APPLICANT", OPERATOR, SENIOR)
                        .requestMatchers("/api/v1/operator/**").hasAnyRole(OPERATOR, SENIOR)
                        .requestMatchers("/internal/intake/files/**", "/internal/intake/documents/**")
                        .hasRole("SERVICE_DOCPROC")
                        .requestMatchers("/internal/intake/applications/**").hasAnyRole("SERVICE_REVIEW", "SERVICE_DOCPROC")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(KeycloakJwt.converter())));
        return http.build();
    }

    /** Проверка JWT Keycloak: подпись, issuer, aud ({@code documents-api} оператора или имя этого сервиса). */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks, @Value("${app.oidc.issuer}") String iss,
                                 @Value("${app.oidc.audiences:documents-api,intake}") List<String> audiences) {
        return KeycloakJwt.decoder(jwks, iss, audiences);
    }
}
