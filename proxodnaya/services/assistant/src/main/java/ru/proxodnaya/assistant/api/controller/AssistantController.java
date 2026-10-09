package ru.proxodnaya.assistant.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.assistant.api.constants.PathConstants;
import ru.proxodnaya.assistant.api.dto.request.AskRq;
import ru.proxodnaya.assistant.api.dto.response.AskRs;
import ru.proxodnaya.assistant.api.dto.response.StatusRs;

/**
 * Помощник руководителя (FR-42..46).
 */
@Tag(name = "Assistant", description = "Помощник руководителя")
@RequestMapping(PathConstants.ASSISTANT)
public interface AssistantController {

    /**
     * Вопрос: модель зовёт инструменты (данные — с правами спросившего), числа ответа проверяются.
     *
     * @param request вопрос
     * @return ответ, источники, итог проверки
     */
    @Operation(summary = "Спросить помощника")
    @PostMapping(PathConstants.ASK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    AskRs ask(@Valid @RequestBody AskRq request);

    /**
     * Провайдер, модель и последние вызовы.
     *
     * @return состояние
     */
    @Operation(summary = "Состояние помощника")
    @GetMapping(PathConstants.STATUS)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    StatusRs status();
}
