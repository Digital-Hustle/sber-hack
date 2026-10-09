package ru.proxodnaya.people.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.ActivateRq;
import ru.proxodnaya.people.api.dto.request.DeviceChallengeRq;
import ru.proxodnaya.people.api.dto.request.DeviceTokenRq;
import ru.proxodnaya.people.api.dto.response.ActivationRs;
import ru.proxodnaya.people.api.dto.response.DeviceChallengeRs;
import ru.proxodnaya.people.api.dto.response.TokenRs;

/**
 * Вход рабочего без пароля. Публичный: рабочего подтверждает одноразовый инвайт (rate-limit — gateway, GW-05).
 */
@Tag(name = "Auth", description = "Активация телефона и device-токен рабочего")
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

    /**
     * Одноразовый challenge для device-токена (PPL-05).
     *
     * @param request id устройства
     * @return challenge и срок
     */
    @Operation(summary = "Получить challenge для входа телефона")
    @PostMapping(PathConstants.DEVICE_CHALLENGE)
    DeviceChallengeRs deviceChallenge(@Valid @RequestBody DeviceChallengeRq request);

    /**
     * Device-токен по подписи challenge ключом телефона (PPL-05, ADR-040).
     *
     * @param request id устройства, challenge, подпись
     * @return JWT people с ролью WORKER; 400 CHALLENGE_INVALID, 403 DEVICE_UNKNOWN | DEVICE_REVOKED | DEVICE_MISMATCH | WORKER_BLOCKED
     */
    @Operation(summary = "Получить device-токен телефона")
    @PostMapping(PathConstants.DEVICE_TOKEN)
    TokenRs deviceToken(@Valid @RequestBody DeviceTokenRq request);
}
