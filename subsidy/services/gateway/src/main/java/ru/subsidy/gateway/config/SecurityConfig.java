package ru.subsidy.gateway.config;

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
 * Безопасность на входе (FR-60, rules.md §2).
 * SSE по одноразовому билету проходит к сервису, который проверяет билет сам;
 * всё остальное — только с валидным JWT Keycloak realm subsidy; внутренние ручки наружу не видны.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Цепочка фильтров.
     *
     * @param http настройка безопасности WebFlux
     * @return цепочка фильтров
     */
    @Bean
    public SecurityWebFilterChain chain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .headers(headers -> headers.frameOptions(
                        frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/*/internal/**", "/internal/**").denyAll()
                        .pathMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/*/api/v1/stream", "/*/api/v1/stream/events").permitAll()
                        .pathMatchers("/package/**", "/docproc/**", "/rules/**", "/assistant/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }

    /**
     * JWKS из внутренней сети, issuer — публичный адрес Keycloak.
     *
     * @param jwks   адрес ключей Keycloak
     * @param issuer ожидаемый issuer токена
     * @return декодер JWT
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks,
                                         @Value("${app.oidc.issuer}") String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
