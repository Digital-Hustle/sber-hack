package ru.proxodnaya.people.api.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import ru.proxodnaya.common.mapper.BaseMapperConfig;
import ru.proxodnaya.people.api.dto.response.CheckpointRs;
import ru.proxodnaya.people.api.dto.response.SiteRs;
import ru.proxodnaya.people.api.dto.response.ZoneRs;
import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.model.Site;
import ru.proxodnaya.people.features.site.model.Zone;

/**
 * Объекты, зоны, проходные → DTO.
 */
@Mapper(config = BaseMapperConfig.class)
public interface SiteApiMapper {

    SiteRs toRs(Site site);

    ZoneRs toRs(Zone zone);

    CheckpointRs toRs(Checkpoint checkpoint);

    List<SiteRs> toSites(List<Site> sites);

    List<ZoneRs> toZones(List<Zone> zones);

    List<CheckpointRs> toCheckpoints(List<Checkpoint> checkpoints);
}
