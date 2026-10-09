package ru.proxodnaya.people.api.mapper;

import org.mapstruct.Mapper;

import ru.proxodnaya.common.mapper.BaseMapperConfig;
import ru.proxodnaya.people.api.dto.request.CreateWorkerRq;
import ru.proxodnaya.people.api.dto.request.UpdateWorkerRq;
import ru.proxodnaya.people.api.dto.response.PageRs;
import ru.proxodnaya.people.api.dto.response.WorkerPageRs;
import ru.proxodnaya.people.api.dto.response.WorkerRs;
import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerDraft;
import ru.proxodnaya.people.features.worker.model.WorkerPage;

/**
 * DTO ↔ модели рабочих.
 */
@Mapper(config = BaseMapperConfig.class)
public interface WorkerApiMapper {

    WorkerDraft toDraft(CreateWorkerRq request);

    WorkerDraft toDraft(UpdateWorkerRq request);

    WorkerRs toRs(Worker worker);

    /**
     * Страница.
     *
     * @param page модель страницы
     * @return DTO
     */
    default WorkerPageRs toRs(WorkerPage page) {
        return new WorkerPageRs(page.items().stream().map(this::toRs).toList(), new PageRs(page.next(), page.total()));
    }
}
