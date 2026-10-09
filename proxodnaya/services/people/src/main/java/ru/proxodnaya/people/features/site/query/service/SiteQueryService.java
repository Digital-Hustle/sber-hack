package ru.proxodnaya.people.features.site.query.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.site.api.ZoneDirectory;
import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.model.Site;
import ru.proxodnaya.people.features.site.model.Zone;
import ru.proxodnaya.people.features.site.query.repository.SiteReadRepository;

/**
 * Чтение объектов, зон, проходных.
 */
@Service
public class SiteQueryService implements ZoneDirectory {

    private final SiteReadRepository repository;

    public SiteQueryService(SiteReadRepository repository) {
        this.repository = repository;
    }

    /**
     * Объекты.
     *
     * @return объекты
     */
    public List<Site> sites() {
        return repository.sites();
    }

    /**
     * Зоны.
     *
     * @param siteId объект или {@code null}
     * @return зоны
     */
    public List<Zone> zones(UUID siteId) {
        return repository.zones(siteId);
    }

    /**
     * Проходные.
     *
     * @param zoneId зона или {@code null}
     * @return проходные
     */
    public List<Checkpoint> checkpoints(UUID zoneId) {
        return repository.checkpoints(zoneId);
    }

    /**
     * Проходная.
     *
     * @param id проходная
     * @return проходная
     * @throws DomainException NOT_FOUND
     */
    public Checkpoint checkpoint(UUID id) {
        return repository.checkpoint(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Проходная не найдена"));
    }

    @Override
    public Set<UUID> missing(Set<UUID> zoneIds) {
        if (zoneIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> missing = new HashSet<>(zoneIds);
        missing.removeAll(repository.existingZones(zoneIds));
        return Set.copyOf(missing);
    }
}
