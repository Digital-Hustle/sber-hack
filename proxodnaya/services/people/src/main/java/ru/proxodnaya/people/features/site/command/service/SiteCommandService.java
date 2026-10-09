package ru.proxodnaya.people.features.site.command.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.exception.ConflictException;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.site.command.repository.SiteRepository;
import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.model.Site;
import ru.proxodnaya.people.features.site.model.Zone;
import ru.proxodnaya.people.features.site.query.repository.SiteReadRepository;

/**
 * Заведение объектов, зон и проходных (PPL-06).
 */
@Service
public class SiteCommandService {

    private final SiteRepository sites;
    private final SiteReadRepository reads;
    private final Clock clock;

    public SiteCommandService(SiteRepository sites, SiteReadRepository reads, Clock clock) {
        this.sites = sites;
        this.reads = reads;
        this.clock = clock;
    }

    /**
     * Новый объект.
     *
     * @param name    название
     * @param address адрес
     * @return объект
     * @throws ConflictException если имя занято
     */
    @Transactional
    public Site createSite(String name, String address) {
        Site site = new Site(UuidCreator.getTimeOrderedEpoch(), name.strip(), address == null ? null : address.strip());
        try {
            sites.insert(site, clock.instant());
        } catch (DuplicateKeyException error) {
            throw new ConflictException("Объект с таким названием уже есть");
        }
        return site;
    }

    /**
     * Новая зона.
     *
     * @param siteId   объект
     * @param name     название
     * @param capacity вместимость
     * @return зона
     * @throws DomainException VALIDATION_ERROR, если объекта нет
     * @throws ConflictException если имя в объекте занято
     */
    @Transactional
    public Zone createZone(UUID siteId, String name, Integer capacity) {
        if (!reads.siteExists(siteId)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Объект не найден");
        }
        Zone zone = new Zone(UuidCreator.getTimeOrderedEpoch(), siteId, name.strip(), capacity);
        try {
            sites.insert(zone, clock.instant());
        } catch (DuplicateKeyException error) {
            throw new ConflictException("Зона с таким названием на объекте уже есть");
        }
        return zone;
    }

    /**
     * Новая проходная.
     *
     * @param zoneId зона, куда ведёт проходная
     * @param name   название
     * @return проходная
     * @throws DomainException VALIDATION_ERROR, если зоны нет
     * @throws ConflictException если имя в зоне занято
     */
    @Transactional
    public Checkpoint createCheckpoint(UUID zoneId, String name) {
        Zone zone = reads.zone(zoneId).orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR, "Зона не найдена"));
        Checkpoint checkpoint = new Checkpoint(UuidCreator.getTimeOrderedEpoch(), zoneId, zone.siteId(), name.strip());
        try {
            sites.insert(checkpoint, clock.instant());
        } catch (DuplicateKeyException error) {
            throw new ConflictException("Проходная с таким названием в зоне уже есть");
        }
        return checkpoint;
    }
}
