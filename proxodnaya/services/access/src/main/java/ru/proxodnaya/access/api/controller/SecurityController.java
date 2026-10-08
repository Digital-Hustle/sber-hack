package ru.proxodnaya.access.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.response.JournalVerifyRs;

/**
 * Служба безопасности: целостность журнала.
 */
@Tag(name = "Security", description = "Проверка журнала проходов")
@RequestMapping(PathConstants.SECURITY)
public interface SecurityController {

    /**
     * Проверяет hash-chain журнала.
     *
     * @return цела ли цепочка
     */
    @Operation(summary = "Проверить целостность журнала (hash-chain)")
    @PostMapping(PathConstants.JOURNAL_VERIFY)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('SECURITY_OFFICER', 'ADMIN')")
    JournalVerifyRs verifyJournal();
}
