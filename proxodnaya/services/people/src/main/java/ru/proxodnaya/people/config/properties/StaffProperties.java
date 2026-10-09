package ru.proxodnaya.people.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Вход персонала по коду из письма ({@code people.staff.*}, PPL-13, ADR-041).
 *
 * @param pepper         секрет HMAC для кодов (как у инвайтов — {@code INVITE_PEPPER})
 * @param codeTtl        срок кода
 * @param resendAfter    повторная отправка не раньше
 * @param maxAttempts    неверных попыток до сгорания кода
 * @param accessTtl      срок access-токена (отключённый теряет доступ не позже)
 * @param sessionTtl     срок сессии (refresh)
 * @param loginUrl       адрес страницы входа — для письма
 * @param mailFrom       отправитель писем
 * @param bootstrapEmail почта первого администратора (создаётся, если персонала нет)
 * @param logCodes       писать код в лог, когда SMTP не настроен (только локальная разработка)
 */
@Validated
@ConfigurationProperties("people.staff")
public record StaffProperties(@NotBlank @Size(min = MIN_PEPPER) String pepper, @NotNull Duration codeTtl, @NotNull Duration resendAfter,
                              @Min(1) int maxAttempts, @NotNull Duration accessTtl, @NotNull Duration sessionTtl,
                              @NotBlank String loginUrl, @NotBlank String mailFrom, String bootstrapEmail,
                              boolean logCodes) {

    /** Минимальная длина секрета. */
    public static final int MIN_PEPPER = 32;
}
