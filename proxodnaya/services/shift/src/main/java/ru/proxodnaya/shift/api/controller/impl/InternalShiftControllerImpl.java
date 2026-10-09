package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.shift.api.controller.InternalShiftController;
import ru.proxodnaya.shift.api.dto.response.ActiveShiftRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.shift.query.service.ShiftQueryService;

/**
 * Реализация внутреннего API смен.
 */
@RestController
public class InternalShiftControllerImpl implements InternalShiftController {

    private final ShiftQueryService shifts;
    private final ShiftApiMapper mapper;

    public InternalShiftControllerImpl(ShiftQueryService shifts, ShiftApiMapper mapper) {
        this.shifts = shifts;
        this.mapper = mapper;
    }

    @Override
    public ActiveShiftRs activeShift(String id, Instant moment) {
        return shifts.activeShift(id, moment).map(mapper::convert)
                .orElseThrow(() -> new NotFoundException("Смен в этот день нет"));
    }
}
