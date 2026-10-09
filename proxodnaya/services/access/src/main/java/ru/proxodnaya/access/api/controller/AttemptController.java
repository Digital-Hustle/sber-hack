package ru.proxodnaya.access.api.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.response.AttemptPageRs;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Журнал проходов в кабинете (FR-10/31): фильтры в query-параметрах (ими можно поделиться ссылкой), keyset-курсор.
 */
@Tag(name = "Attempts", description = "Журнал проходов")
@RequestMapping(PathConstants.ATTEMPTS)
public interface AttemptController {

    /**
     * Журнал проходов, новые сверху.
     *
     * @param from       начало периода, включительно (ISO-8601)
     * @param until      конец периода, не включая (ISO-8601), параметр {@code to}
     * @param checkpoint проходная
     * @param worker     рабочий
     * @param decision   решение
     * @param reason     код причины
     * @param source     источник
     * @param direction  направление
     * @param cursor     {@code page.next} предыдущей страницы
     * @param limit      1..200, по умолчанию 50
     * @return страница
     */
    @Operation(summary = "Журнал проходов: фильтры и курсор")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    AttemptPageRs list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                       @RequestParam(name = "to", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                       @RequestParam(required = false) String checkpoint,
                       @RequestParam(required = false) String worker,
                       @RequestParam(required = false) Decision decision,
                       @RequestParam(required = false) ReasonCode reason,
                       @RequestParam(required = false) AttemptSource source,
                       @RequestParam(required = false) Direction direction,
                       @RequestParam(required = false) String cursor,
                       @RequestParam(required = false) Integer limit);
}
