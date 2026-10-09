package ru.subsidy.package_.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.pkg.PackageService;
import ru.subsidy.package_.pkg.PackageService.PackageView;

/**
 * Пакеты документов (FR-01).
 */
@RestController
@RequestMapping("/api/v1/packages")
public class PackagesController {

    private final PackageService packages;

    /**
     * Конструктор.
     *
     * @param packages сервис пакетов
     */
    public PackagesController(PackageService packages) {
        this.packages = packages;
    }

    /**
     * Создаёт пакет.
     *
     * @param request ИНН и программа
     * @param idempotencyKey ключ идемпотентности
     * @param user текущий пользователь
     * @return пакет
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public PackageResponse create(@Valid @RequestBody CreatePackageRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  CurrentUser user) {
        return PackageResponse.of(packages.create(request.orgInn(), request.programCode(), idempotencyKey,
                user.userId()));
    }

    /**
     * Карточка пакета.
     *
     * @param packageId id пакета
     * @return пакет
     */
    @GetMapping("/{packageId}")
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    public PackageResponse get(@PathVariable UUID packageId) {
        return PackageResponse.of(packages.get(packageId));
    }

    /**
     * Запрос создания пакета.
     *
     * @param orgInn ИНН заявителя
     * @param programCode программа
     */
    public record CreatePackageRequest(@NotBlank String orgInn, @NotBlank String programCode) {
    }

    /**
     * Ответ.
     *
     * @param packageId id
     * @param status статус
     * @param program программа
     * @param org организация
     */
    public record PackageResponse(UUID packageId, String status, String program, Org org) {

        static PackageResponse of(PackageView view) {
            return new PackageResponse(view.id(), view.status(), view.programCode(),
                    new Org(view.orgInn(), view.orgName()));
        }
    }

    /**
     * Организация в ответе.
     *
     * @param inn ИНН
     * @param name наименование
     */
    public record Org(String inn, String name) {
    }
}
