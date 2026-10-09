package ru.proxodnaya.people.api.mapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ru.proxodnaya.people.api.dto.request.CreateWorkerRq;
import ru.proxodnaya.people.api.dto.request.PermitRq;
import ru.proxodnaya.people.api.dto.request.UpdateWorkerRq;
import ru.proxodnaya.people.api.dto.response.PageRs;
import ru.proxodnaya.people.api.dto.response.PermitRs;
import ru.proxodnaya.people.api.dto.response.WorkerPageRs;
import ru.proxodnaya.people.api.dto.response.WorkerRs;
import ru.proxodnaya.people.features.worker.model.Permit;
import ru.proxodnaya.people.features.worker.model.PermitType;
import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerDraft;
import ru.proxodnaya.people.features.worker.model.WorkerPage;
import ru.proxodnaya.people.features.worker.model.WorkerView;

/**
 * DTO ↔ модели рабочих. Вручную: в ответе собираются карточка, зоны и допуски.
 */
@Component
public class WorkerApiMapper {

    /**
     * Создание.
     *
     * @param request DTO
     * @return черновик
     */
    public WorkerDraft toDraft(CreateWorkerRq request) {
        return new WorkerDraft(request.fullName(), request.position(), request.contractor(),
                request.zoneIds() == null ? Set.of() : Set.copyOf(request.zoneIds()), permits(request.permits()));
    }

    /**
     * Изменение.
     *
     * @param request DTO
     * @return черновик ({@code null} — не менять)
     */
    public WorkerDraft toDraft(UpdateWorkerRq request) {
        return new WorkerDraft(request.fullName(), request.position(), request.contractor(),
                request.zoneIds() == null ? null : Set.copyOf(request.zoneIds()),
                request.permits() == null ? null : permits(request.permits()));
    }

    /**
     * Рабочий.
     *
     * @param view рабочий с допусками
     * @return DTO
     */
    public WorkerRs toRs(WorkerView view) {
        Worker worker = view.worker();
        return new WorkerRs(worker.id().toString(), worker.fullName(), worker.position(), worker.contractor(),
                worker.status().name(),
                view.zoneIds().stream().map(UUID::toString).sorted().toList(),
                view.permits().stream().map(permit -> new PermitRs(permit.type().name(), permit.validUntil())).toList(),
                view.permitUntil(), worker.createdAt(), worker.updatedAt());
    }

    /**
     * Страница.
     *
     * @param page модель
     * @return DTO
     */
    public WorkerPageRs toRs(WorkerPage page) {
        return new WorkerPageRs(page.items().stream().map(this::toRs).toList(), new PageRs(page.next(), page.total()));
    }

    private static List<Permit> permits(List<PermitRq> permits) {
        return permits == null ? List.of() : permits.stream()
                .map(permit -> new Permit(PermitType.valueOf(permit.type()), permit.validUntil()))
                .toList();
    }
}
