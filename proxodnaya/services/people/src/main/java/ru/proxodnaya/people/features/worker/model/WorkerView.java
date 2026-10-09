package ru.proxodnaya.people.features.worker.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Рабочий с допусками в зоны и сроками допусков.
 *
 * @param worker  рабочий
 * @param zoneIds зоны, куда есть допуск
 * @param permits допуски со сроком
 */
public record WorkerView(Worker worker, Set<UUID> zoneIds, List<Permit> permits) {

    /**
     * Ближайший срок окончания допусков: без них проход закрыт.
     *
     * @return дата или {@code null}, если допусков нет
     */
    public LocalDate permitUntil() {
        return permits.stream().map(Permit::validUntil).min(Comparator.naturalOrder()).orElse(null);
    }
}
