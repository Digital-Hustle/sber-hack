package ru.subsidy.package_.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

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

    /** Полномочие service account svc-docproc. */
    public static final String SVC_DOCPROC = "SVC_DOCPROC";
    private static final String ROLES_CLAIM = "roles";
    private static final String CLIENT_CLAIM = "azp";
    private static final String SERVICE_PREFIX = "svc-";

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
                        .requestMatchers("/internal/**").hasAuthority(SVC_DOCPROC)
                        .requestMatchers("/api/v1/stream").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(currentUserConverter())))
                .build();
    }

    // Не @Bean: Kafka регистрирует все Converter-бины и падает на лямбде без типов.
    private static Converter<Jwt, AbstractAuthenticationToken> currentUserConverter() {
        return jwt -> new CurrentUser(jwt, authorities(jwt));
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> result = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null) {
            roles.forEach(role -> result.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        String client = jwt.getClaimAsString(CLIENT_CLAIM);
        if (client != null && client.startsWith(SERVICE_PREFIX)) {
            String name = client.substring(SERVICE_PREFIX.length()).toUpperCase(Locale.ROOT);
            result.add(new SimpleGrantedAuthority("SVC_" + name));
        }
        return result;
    }
}
