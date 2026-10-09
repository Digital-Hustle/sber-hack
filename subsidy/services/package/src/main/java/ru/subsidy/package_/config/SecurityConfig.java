package ru.subsidy.package_.config;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

import ru.subsidy.common.security.CurrentUser;

/**
 * Доступ к package: только JWT Keycloak realm subsidy, роли — из claim {@code roles} (FR-60).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLES_CLAIM = "roles";

    /**
     * Цепочка фильтров.
     *
     * @param http настройка безопасности
     * @return цепочка фильтров
     * @throws Exception ошибка конфигурации
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(currentUserConverter())))
                .build();
    }

    /**
     * JWT → {@link CurrentUser} с ролями realm.
     *
     * @return конвертер
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> currentUserConverter() {
        return jwt -> new CurrentUser(jwt, authorities(jwt));
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
