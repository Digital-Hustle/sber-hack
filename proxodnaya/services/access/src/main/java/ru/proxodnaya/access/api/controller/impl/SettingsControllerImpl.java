package ru.proxodnaya.access.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;
import ru.proxodnaya.access.api.controller.SettingsController;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.features.pass.query.service.SettingsService;

@RestController
public class SettingsControllerImpl implements SettingsController {

    private final SettingsService service;
    public SettingsControllerImpl(SettingsService service) { this.service = service; }

    @Override public SettingsRs get() { return service.get(); }
    @Override public SettingsRs update(SettingsRs s) { return service.update(s); }
}