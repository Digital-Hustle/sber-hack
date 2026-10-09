package ru.proxodnaya.people.config;

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
import org.springframework.security.web.SecurityFilterChain;

import ru.proxodnaya.common.security.CurrentUser;
import ru.proxodnaya.common.security.TrustedIssuers;
import ru.proxodnaya.people.api.constants.PathConstants;

/**
 * Resource server: JWT двух издателей — Keycloak и people (ADR-040), роли из {@code realm_access.roles} → {@code ROLE_*}
 * (docs/SECURITY.md).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**").permitAll()
                        // рабочего без пароля подтверждает одноразовый инвайт; rate-limit — на gateway (GW-05)
                        .requestMatchers(HttpMethod.POST, PathConstants.AUTH + PathConstants.ACTIVATE).permitAll()
                        // device-токен: телефон подтверждает себя подписью challenge (PPL-05)
                        .requestMatchers(HttpMethod.POST, PathConstants.AUTH + PathConstants.DEVICE_CHALLENGE,
                                PathConstants.AUTH + PathConstants.DEVICE_TOKEN).permitAll()
                        // вход персонала: подтверждает код из письма (PPL-13), продление — refresh-токен
                        .requestMatchers(HttpMethod.POST, PathConstants.AUTH + PathConstants.STAFF_CODE,
                                PathConstants.AUTH + PathConstants.STAFF_VERIFY,
                                PathConstants.AUTH + PathConstants.STAFF_REFRESH).permitAll()
                        // открытые ключи токенов people — для gateway и сервисов во внутренней сети
                        .requestMatchers(HttpMethod.GET, PathConstants.INTERNAL + PathConstants.JWKS).permitAll()
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
