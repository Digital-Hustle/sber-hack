package ru.proxodnaya.people.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh сессии персонала в httpOnly-cookie (PPL-14), {@code people.staff-cookie.*}: перезагрузка кабинета не требует
 * повторного входа, а скрипты страницы refresh не видят.
 *
 * @param name   имя cookie
 * @param path   путь cookie в браузере (с префиксом gateway)
 * @param secure только по https (за edge/Caddy — да; локально по http — {@code false})
 */
@Validated
@ConfigurationProperties("people.staff-cookie")
public record StaffCookieProperties(@NotBlank String name, @NotBlank String path, boolean secure) {
}
