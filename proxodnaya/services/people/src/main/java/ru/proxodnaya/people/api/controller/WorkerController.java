package ru.proxodnaya.people.api.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.CheckFaceRq;
import ru.proxodnaya.people.api.dto.request.CreateWorkerRq;
import ru.proxodnaya.people.api.dto.request.EnrollFaceRq;
import ru.proxodnaya.people.api.dto.request.UpdateWorkerRq;
import ru.proxodnaya.people.api.dto.response.FaceCheckRs;
import ru.proxodnaya.people.api.dto.response.FaceEnrollRs;
import ru.proxodnaya.people.api.dto.response.WorkerPageRs;
import ru.proxodnaya.people.api.dto.response.WorkerRs;

/**
 * Рабочие (кабинет). Читают ADMIN, MANAGER, SECURITY_OFFICER; меняет только ADMIN.
 */
@Tag(name = "Workers", description = "Рабочие подрядчиков")
@RequestMapping(PathConstants.WORKERS)
public interface WorkerController {

    /**
     * Список с поиском, фильтром и курсором.
     *
     * @param query  поиск по ФИО, должности, подрядчику
     * @param filter all | active | blocked
     * @param cursor курсор из {@code page.next}
     * @param limit  1..200, по умолчанию 50
     * @return страница
     */
    @Operation(summary = "Список рабочих")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    WorkerPageRs list(@RequestParam(name = "q", required = false) String query, @RequestParam(required = false) String filter,
                      @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit);

    /**
     * Карточка.
     *
     * @param id рабочий
     * @return рабочий
     */
    @Operation(summary = "Карточка рабочего")
    @GetMapping(PathConstants.WORKER_ID)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    WorkerRs get(@PathVariable UUID id);

    /**
     * Создать.
     *
     * @param request данные
     * @return рабочий
     */
    @Operation(summary = "Завести рабочего")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    WorkerRs create(@Valid @RequestBody CreateWorkerRq request);

    /**
     * Изменить карточку.
     *
     * @param id рабочий
     * @param request изменения
     * @return рабочий
     */
    @Operation(summary = "Изменить карточку рабочего")
    @PatchMapping(PathConstants.WORKER_ID)
    @PreAuthorize("hasRole('ADMIN')")
    WorkerRs update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkerRq request);

    /**
     * Заблокировать.
     *
     * @param id рабочий
     * @return рабочий
     */
    @Operation(summary = "Заблокировать рабочего")
    @PostMapping(PathConstants.BLOCK)
    @PreAuthorize("hasRole('ADMIN')")
    WorkerRs block(@PathVariable UUID id);

    /**
     * Разблокировать.
     *
     * @param id рабочий
     * @return рабочий
     */
    @Operation(summary = "Разблокировать рабочего")
    @PostMapping(PathConstants.UNBLOCK)
    @PreAuthorize("hasRole('ADMIN')")
    WorkerRs unblock(@PathVariable UUID id);

    /**
     * Удалить (мягко).
     *
     * @param id рабочий
     */
    @Operation(summary = "Удалить рабочего")
    @DeleteMapping(PathConstants.WORKER_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable UUID id);

    /**
     * Завести или переснять эталон лица: снимки уходят в face, плохой снимок — 422 с причиной.
     *
     * @param id      рабочий
     * @param request снимки
     * @param actor   кто заводит (subject токена)
     * @return итог
     */
    @Operation(summary = "Завести эталон лица")
    @PostMapping(PathConstants.FACE)
    @PreAuthorize("hasRole('ADMIN')")
    FaceEnrollRs enrollFace(@PathVariable UUID id, @Valid @RequestBody EnrollFaceRq request, Principal actor);

    /**
     * Удалить эталон лица (проход по лицу станет невозможен).
     *
     * @param id рабочий
     */
    @Operation(summary = "Удалить эталон лица")
    @DeleteMapping(PathConstants.FACE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteFace(@PathVariable UUID id);

    /**
     * Проверить снимок до сохранения карточки.
     *
     * @param request снимок
     * @return вердикт с подсказкой
     */
    @Operation(summary = "Проверить снимок лица")
    @PostMapping(PathConstants.FACE_CHECK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    FaceCheckRs checkFace(@Valid @RequestBody CheckFaceRq request);
}
