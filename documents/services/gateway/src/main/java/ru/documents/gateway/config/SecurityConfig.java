package ru.documents.gateway.config;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import ru.documents.gateway.limit.RateLimitFilter;
import ru.documents.gateway.limit.RateLimitProperties;
import ru.documents.gateway.limit.RateLimiter;

/**
 * Безопасность на входе (FR-50, FR-64, FR-06).
 * Соискатель (ключ заявки) и SSE по одноразовому билету проходят к сервису, который их проверяет сам;
 * всё остальное — только с валидным JWT Keycloak; внутренние ручки наружу не видны (404);
 * CORS — только свои origin; создание заявок, загрузки и вход по коду ограничены по IP.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    /** Цепочка фильтров. */
    @Bean
    public SecurityWebFilterChain chain(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(c -> c.configurationSource(cors))
                .headers(h -> h.frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)))
                .authorizeExchange(a -> a
                        // prometheus — для сбора внутри сети; наружу Caddy отдаёт только health (attack.sh проверяет)
                        .pathMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/intake/api/v1/applicant/**", "/intake/api/v1/files/**").permitAll()
                        .pathMatchers("/*/api/v1/stream", "/*/api/v1/stream/events").permitAll()
                        .pathMatchers("/intake/**", "/docproc/**", "/review/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> { }))
                .build();
    }

    /** CORS: фронт и API на одном домене за Caddy, поэтому разрешаем только свои origin из настроек. */
    @Bean
    public CorsConfigurationSource corsSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origins.stream().filter(o -> !o.isBlank()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Last-Event-ID"));
        c.setExposedHeaders(List.of("Retry-After", "Content-Disposition"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    /** JWKS из внутренней сети, issuer — публичный адрес Keycloak, aud — токен выдан для API (клиент оператора). */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${app.oidc.jwk-set-uri}") String jwks,
                                         @Value("${app.oidc.issuer}") String issuer,
                                         @Value("${app.oidc.audience:documents-api}") String audience) {
        NimbusReactiveJwtDecoder d = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> aud = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "aud mismatch", null));
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud));
        return d;
    }

    /** Часы (в тестах подменяются). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** 404 для внутренних ручек — раньше проверки токена. */
    @Bean
    public InternalPathFilter internalPathFilter() {
        return new InternalPathFilter();
    }

    /** Лимиты частоты. */
    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties props, Clock clock) {
        return new RateLimitFilter(props, new RateLimiter(clock));
    }
}
