package ru.proxodnaya.access.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.SecurityController;
import ru.proxodnaya.access.api.dto.response.JournalVerifyRs;
import ru.proxodnaya.access.api.mapper.PassApiMapper;
import ru.proxodnaya.access.features.pass.command.service.JournalService;

/**
 * Реализация API службы безопасности.
 */
@RestController
public class SecurityControllerImpl implements SecurityController {

    private final JournalService journal;
    private final PassApiMapper mapper;

    public SecurityControllerImpl(JournalService journal, PassApiMapper mapper) {
        this.journal = journal;
        this.mapper = mapper;
    }

    @Override
    public JournalVerifyRs verifyJournal() {
        return mapper.convert(journal.verify());
    }
}
