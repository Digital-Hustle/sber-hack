package ru.proxodnaya.access.features.pass.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.config.properties.SimulatorProperties;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.model.SimulationRequest;
import ru.proxodnaya.access.features.pass.model.SimulationResult;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Демо-история (FR-34, ADR-007): попытки пишутся тем же {@link PassRecorder} в журнал и {@code access.pass.v1}
 * с {@code source = SIMULATOR}; shift помечает интервалы {@code simulated}, аналитика по умолчанию их исключает.
 */
@Service
public class SimulatorService {

    private final PeopleGateway people;
    private final PassRecorder recorder;
    private final SimulatorProperties props;
    private final AccessProperties access;
    private final Clock clock;

    public SimulatorService(PeopleGateway people, PassRecorder recorder, SimulatorProperties props, AccessProperties access,
                            Clock clock) {
        this.people = people;
        this.recorder = recorder;
        this.props = props;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Генерирует и записывает историю.
     *
     * @param request что сгенерировать
     * @return сколько записано
     * @throws DomainException   симулятор выключен ({@code FORBIDDEN}) или параметры вне лимитов
     * @throws NotFoundException проходной нет в people
     */
    public SimulationResult run(SimulationRequest request) {
        if (!props.enabled()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Симулятор выключен (SIMULATOR_ENABLED=false)");
        }
        List<String> workers = request.workerIds().stream().map(String::strip).filter(id -> !id.isEmpty()).distinct().toList();
        if (workers.isEmpty() || workers.size() > props.maxWorkers() || request.days() < 1 || request.days() > props.maxDays()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Рабочих — от 1 до " + props.maxWorkers() + ", дней — от 1 до " + props.maxDays());
        }
        String zoneId = people.checkpointZone(request.checkpointId())
                .orElseThrow(() -> new NotFoundException("Проходная не найдена"));
        Random random = request.seed() == null ? new Random() : new Random(request.seed());
        LocalDate today = LocalDate.now(clock.withZone(access.siteZone()));
        List<PassRecord> records = new SimulationGenerator(access.siteZone())
                .generate(request.checkpointId(), zoneId, workers, request.days(), request.denyRate(), today, random);
        if (records.isEmpty()) {
            return new SimulationResult(0, 0, 0, null, null);
        }
        recorder.simulated(records);
        int allowed = (int) records.stream().filter(rec -> rec.decision() == Decision.ALLOW).count();
        return new SimulationResult(records.size(), allowed, records.size() - allowed, records.getFirst().ts(), records.getLast().ts());
    }
}
