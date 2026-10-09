package ru.proxodnaya.shift.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.ShiftTemplateController;
import ru.proxodnaya.shift.api.dto.request.ShiftTemplateRq;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplateRs;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplatesRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.shift.command.service.ShiftCommandService;
import ru.proxodnaya.shift.features.shift.query.service.ShiftQueryService;

/**
 * Реализация API шаблонов смен.
 */
@RestController
public class ShiftTemplateControllerImpl implements ShiftTemplateController {

    private final ShiftQueryService query;
    private final ShiftCommandService command;
    private final ShiftApiMapper mapper;

    public ShiftTemplateControllerImpl(ShiftQueryService query, ShiftCommandService command, ShiftApiMapper mapper) {
        this.query = query;
        this.command = command;
        this.mapper = mapper;
    }

    @Override
    public ShiftTemplatesRs list() {
        return mapper.convertTemplates(query.templates());
    }

    @Override
    public ShiftTemplateRs create(ShiftTemplateRq request) {
        return mapper.convert(command.createTemplate(request.name(), request.start(), request.end()));
    }
}
