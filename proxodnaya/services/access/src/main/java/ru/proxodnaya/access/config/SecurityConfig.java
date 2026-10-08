package ru.proxodnaya.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server: JWT от Keycloak + device-токены people.
 * Каждый эндпоинт защищён ролью (GUARD для киоска, ADMIN/MANAGER для админки).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .oauth2ResourceServer(o -> o.jwt(jwt -> {}));
        return http.build();
    }
}
