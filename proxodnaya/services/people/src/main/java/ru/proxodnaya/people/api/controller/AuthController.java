package ru.proxodnaya.people.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.ActivateRq;
import ru.proxodnaya.people.api.dto.response.ActivationRs;

/**
 * Вход рабочего без пароля. Публичный: рабочего подтверждает одноразовый инвайт (rate-limit — gateway, GW-05).
 */
@Tag(name = "Auth", description = "Активация телефона рабочего")
@RequestMapping(PathConstants.AUTH)
public interface AuthController {

    /**
     * Привязывает ключ телефона по инвайту.
     *
     * @param request код, id устройства, публичный ключ
     * @return рабочий, время сервера, карточка
     */
    @Operation(summary = "Активировать телефон по коду приглашения")
    @PostMapping(PathConstants.ACTIVATE)
    ActivationRs activate(@Valid @RequestBody ActivateRq request);
}
