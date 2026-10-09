package ru.documents.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;

/**
 * Безопасность на входе (FR-50, FR-64).
 * Соискатель (ключ заявки) и SSE по одноразовому билету проходят к сервису, который их проверяет сам;
 * всё остальное — только с валидным JWT Keycloak; внутренние ручки наружу не видны.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Цепочка фильтров. */
    @Bean
    public SecurityWebFilterChain chain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .headers(h -> h.frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)))
                .authorizeExchange(a -> a
                        .pathMatchers("/*/internal/**", "/internal/**").denyAll()
                        .pathMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/intake/api/v1/applicant/**", "/intake/api/v1/files/**").permitAll()
                        .pathMatchers("/*/api/v1/stream", "/*/api/v1/stream/events").permitAll()
                        .pathMatchers("/intake/**", "/docproc/**", "/review/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> { }))
                .build();
    }

    /** JWKS из внутренней сети, issuer — публичный адрес Keycloak. */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks,
                                         @Value("${app.oidc.issuer}") String issuer) {
        NimbusReactiveJwtDecoder d = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return d;
    }
}
