package ru.proxodnaya.people.features.worker.command.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.people.features.worker.api.WorkerCard;
import ru.proxodnaya.people.features.worker.api.WorkerDirectory;
import ru.proxodnaya.people.features.worker.command.repository.WorkerRepository;

/**
 * Реализация {@link WorkerDirectory}.
 */
@Service
public class WorkerDirectoryService implements WorkerDirectory {

    private final WorkerRepository workers;

    public WorkerDirectoryService(WorkerRepository workers) {
        this.workers = workers;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<WorkerCard> lock(UUID id) {
        return workers.lock(id).map(worker -> new WorkerCard(worker.id(), worker.fullName(), worker.position(),
                worker.contractor(), worker.status()));
    }
}
