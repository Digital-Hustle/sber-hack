package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.response.AttemptPageRs;

/**
 * История проходов на телефоне рабочего. Только свои: рабочий — из device-токена ({@code sub}), а не из запроса.
 */
@Tag(name = "Worker", description = "Телефон рабочего")
@RequestMapping(PathConstants.WORKER_ATTEMPTS)
public interface WorkerAttemptController {

    /**
     * Свои проходы, новые сверху, с причинами.
     *
     * @param token  device-токен рабочего ({@code sub} — workerId)
     * @param cursor {@code page.next} предыдущей страницы
     * @param limit  1..200, по умолчанию 50
     * @return страница
     */
    @Operation(summary = "Мои проходы: история с причинами")
    @GetMapping
    @PreAuthorize("hasRole('WORKER')")
    AttemptPageRs mine(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                       @RequestParam(required = false) String cursor,
                       @RequestParam(required = false) Integer limit);
}
