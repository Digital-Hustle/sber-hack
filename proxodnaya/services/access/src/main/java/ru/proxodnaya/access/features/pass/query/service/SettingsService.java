package ru.proxodnaya.access.features.pass.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.features.pass.query.repository.SettingsRepository;

@Service
@Transactional
public class SettingsService {

    private final SettingsRepository repo;
    public SettingsService(SettingsRepository repo) { this.repo = repo; }

    public SettingsRs get() { return repo.find(); }
    public SettingsRs update(SettingsRs s) { return repo.update(s); }
}