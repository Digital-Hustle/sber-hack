package ru.proxodnaya.assistant.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.assistant.api.controller.AssistantController;
import ru.proxodnaya.assistant.api.dto.request.AskRq;
import ru.proxodnaya.assistant.api.dto.response.AskRs;
import ru.proxodnaya.assistant.api.dto.response.StatusRs;
import ru.proxodnaya.assistant.api.mapper.AssistantApiMapper;
import ru.proxodnaya.assistant.features.assistant.command.service.AssistantCommandService;
import ru.proxodnaya.assistant.features.assistant.query.service.LlmStatusQueryService;

/**
 * Реализация API помощника.
 */
@RestController
public class AssistantControllerImpl implements AssistantController {

    private final AssistantCommandService assistant;
    private final LlmStatusQueryService status;
    private final AssistantApiMapper mapper;

    public AssistantControllerImpl(AssistantCommandService assistant, LlmStatusQueryService status, AssistantApiMapper mapper) {
        this.assistant = assistant;
        this.status = status;
        this.mapper = mapper;
    }

    @Override
    public AskRs ask(AskRq request) {
        return mapper.convert(assistant.ask(request.question().strip()));
    }

    @Override
    public StatusRs status() {
        return mapper.convert(status.status());
    }
}
