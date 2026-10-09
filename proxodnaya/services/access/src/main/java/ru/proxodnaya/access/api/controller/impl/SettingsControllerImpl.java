package ru.proxodnaya.access.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;
import ru.proxodnaya.access.api.controller.SettingsController;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.features.pass.query.repository.SettingsRepository;

@RestController
public class SettingsControllerImpl implements SettingsController {

    private final SettingsRepository repo;
    public SettingsControllerImpl(SettingsRepository repo) { this.repo = repo; }

    @Override public SettingsRs get() { return repo.find(); }

    @Override public SettingsRs update(SettingsRs s) { return repo.update(s); }
}