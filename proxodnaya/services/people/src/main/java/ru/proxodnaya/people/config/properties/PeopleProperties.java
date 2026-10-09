package ru.proxodnaya.people.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Настройки people ({@code people.*}).
 *
 * @param invite инвайты рабочих
 */
@Validated
@ConfigurationProperties("people")
public record PeopleProperties(@Valid @NotNull Invite invite) {

    /** Минимальная длина секрета HMAC. */
    public static final int MIN_PEPPER = 32;

    /**
     * Инвайт.
     *
     * @param ttl      срок жизни кода
     * @param pepper   секрет HMAC для хэша кода (код короткий — без секрета хэш перебирается за минуты)
     * @param linkBase адрес PWA, куда ведёт ссылка-инвайт
     */
    public record Invite(@NotNull Duration ttl, @NotBlank @Size(min = MIN_PEPPER) String pepper, @NotBlank String linkBase) {
    }
}
