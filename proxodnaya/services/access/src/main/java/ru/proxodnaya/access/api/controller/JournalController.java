package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.AttemptFilterRq;
import ru.proxodnaya.access.api.dto.response.AttemptsRs;

/**
 * Журнал проходов (FR-10/31): фильтры, курсоры, миниатюры только ADMIN.
 */
@Tag(name = "Journal", description = "Журнал проходов с фильтрами")
@RequestMapping(PathConstants.ATTEMPTS)
public interface JournalController {

    /**
     * Список попыток по фильтру.
     *
     * @param filter фильтры (workerId, checkpoint, decision, code, source, from, to, cursor, limit)
     * @return страница журнала
     */
    @Operation(summary = "Журнал проходов: фильтры, курсорная пагинация")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    AttemptsRs list(AttemptFilterRq filter);

    /**
     * История проходов рабочего (для телефона).
     *
     * @param workerId id рабочего
     * @param limit    сколько последних
     * @return список попыток
     */
    @Operation(summary = "Последние проходы рабочего")
    @GetMapping("/worker/{workerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER') or hasRole('WORKER')")
    AttemptsRs workerAttempts(@PathVariable String workerId, @RequestParam(defaultValue = "20") int limit);
}
