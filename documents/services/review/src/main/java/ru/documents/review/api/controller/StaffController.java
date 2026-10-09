package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.documents.review.api.dto.request.EnabledRq;
import ru.documents.review.api.dto.request.StaffRq;
import ru.documents.review.api.dto.response.StaffRs;
import ru.documents.review.features.staff.model.Enrollment;

/**
 * Сотрудники (только старший оператор): завести, выдать новый код, запретить вход.
 */
@Tag(name = "staff")
@RequestMapping("/api/v1/staff")
public interface StaffController {

    /** Список сотрудников. */
    @Operation(summary = "Сотрудники рабочего места")
    @GetMapping
    StaffRs list();

    /** Завести сотрудника: ответ содержит код для QR — он показывается один раз. */
    @Operation(summary = "Завести сотрудника и выдать код")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Enrollment create(@Valid @RequestBody StaffRq rq, Authentication auth);

    /** Новый код: старый перестаёт работать, сессии сотрудника закрываются. */
    @Operation(summary = "Выдать новый код")
    @PostMapping("/{login}/code")
    Enrollment reissue(@PathVariable("login") String login, Authentication auth);

    /** Разрешить или запретить вход. */
    @Operation(summary = "Разрешить или запретить вход")
    @PutMapping("/{login}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void enabled(@PathVariable("login") String login, @Valid @RequestBody EnabledRq rq, Authentication auth);
}
