package ru.proxodnaya.access.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.config.properties.KioskProperties;
import ru.proxodnaya.access.features.kiosk.command.service.KioskAuthenticator;

import ru.proxodnaya.common.security.CurrentUser;
import ru.proxodnaya.common.security.TrustedIssuers;

/**
 * Терминалы — подпись запроса ключом терминала ({@link KioskSignatureFilter}, ACC-12).
 * Resource server: JWT двух издателей — Keycloak и people (ADR-040), роли из {@code realm_access.roles} → {@code ROLE_*}
 * (docs/SECURITY.md).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env, KioskAuthenticator kiosks, ObjectMapper objectMapper,
                                            KioskProperties kioskProps) throws Exception {
        http
                .addFilterBefore(new KioskSignatureFilter(kiosks, objectMapper, kioskProps.pathPrefix()),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**").permitAll()
                        // первый запуск терминала: ключ ещё не известен, подписывать нечем (ACC-12)
                        .requestMatchers(HttpMethod.POST, PathConstants.KIOSK_REGISTER).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(TrustedIssuers.resolver(env, realmRoles())));
        return http.build();
    }

    static Converter<Jwt, AbstractAuthenticationToken> realmRoles() {
        return jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");
            Collection<GrantedAuthority> authorities = roles instanceof List<?> list
                    ? list.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList()
                    : List.of();
            return new CurrentUser(jwt, authorities);
        };
    }
}
