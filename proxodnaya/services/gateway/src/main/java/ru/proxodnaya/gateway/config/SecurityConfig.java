package ru.proxodnaya.gateway.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import ru.proxodnaya.gateway.config.properties.GatewayProperties;
import ru.proxodnaya.gateway.exception.Problems;

/**
 * JWT нескольких издателей (GW-03): подпись, срок и {@code iss}; роли и audience проверяет сервис.
 * Без токена — только health, активация телефона по инвайту и SSE по одноразовому тикету (сервис проверит тикет).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Пути без токена — явный список (RULES gateway). */
    static final String[] PUBLIC_POST = {"/people/api/v1/auth/worker/activate", "/people/api/v1/auth/device/challenge",
        "/people/api/v1/auth/device/token"};
    // Actuator gateway (/actuator/**) — только на management-порту (наружу не публикуется); на основном порту маршрута нет → 404
    static final String[] PUBLIC_GET = {"/actuator/health", "/actuator/health/**", "/actuator/prometheus", "/actuator/info",
        "/shift/api/v1/stream/situation", "/shift/api/v1/stream/worker"};
    private static final long CORS_MAX_AGE = 3600L;
    private static final String REQUEST_ID = "X-Request-Id";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  ReactiveAuthenticationManagerResolver<ServerWebExchange> issuers,
                                                  CorsConfigurationSource cors) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(spec -> spec.configurationSource(cors))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .pathMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationManagerResolver(issuers)
                        .authenticationEntryPoint((exchange, error) -> unauthorized(exchange))
                        .accessDeniedHandler((exchange, error) ->
                                Problems.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа")))
                .exceptionHandling(spec -> spec.authenticationEntryPoint((exchange, error) -> unauthorized(exchange)))
                .build();
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return Problems.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Нужен действующий токен");
    }

    @Bean
    ReactiveAuthenticationManagerResolver<ServerWebExchange> issuers(GatewayProperties props,
                                                                    Function<GatewayProperties.Issuer, ReactiveJwtDecoder> decoders) {
        Map<String, ReactiveAuthenticationManager> managers = props.issuers().stream().collect(Collectors.toMap(
                GatewayProperties.Issuer::issuer, issuer -> new JwtReactiveAuthenticationManager(decoders.apply(issuer))));
        return new JwtIssuerReactiveAuthenticationManagerResolver(issuer -> Mono.justOrEmpty(managers.get(issuer)));
    }

    @Bean
    Function<GatewayProperties.Issuer, ReactiveJwtDecoder> jwtDecoders() {
        return issuer -> {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(issuer.jwkSetUri())
                    // Keycloak подписывает RS256, people (телефоны и персонал, ADR-040) — ES256
                    .jwsAlgorithm(SignatureAlgorithm.RS256).jwsAlgorithm(SignatureAlgorithm.ES256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer.issuer())));
            return decoder;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", REQUEST_ID, "X-Kiosk-Id", "X-Ts",
                "X-Nonce", "X-Signature", "Idempotency-Key", "traceparent"));
        config.setExposedHeaders(List.of(REQUEST_ID, "Retry-After"));
        config.setMaxAge(CORS_MAX_AGE);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
