package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.application.SimulationGenerator;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;

/**
 * Генератор демо-истории: будни, пара вход/выход, детерминизм по зерну.
 */
class SimulationGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDate MONDAY = LocalDate.of(2025, 6, 9);
    private static final List<String> WORKERS = List.of("w1", "w2");
    private static final long SEED = 42L;
    private static final int WEEK = 7;
    private static final int WORKDAYS = 5;

    private final SimulationGenerator generator = new SimulationGenerator(ZONE);

    @Test
    void generate_week_onlyWeekdaysWithInAndOutPerWorker() {
        // when
        List<PassRecord> records = generator.generate("cp", "zone", WORKERS, WEEK, 0, MONDAY, new Random(SEED));

        // then
        assertThat(records).hasSize(WORKDAYS * WORKERS.size() * 2);
        assertThat(records).allSatisfy(rec -> {
            assertThat(rec.source()).isEqualTo(AttemptSource.SIMULATOR);
            assertThat(rec.decision()).isEqualTo(Decision.ALLOW);
            assertThat(rec.ts().atZone(ZONE).getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(rec.ts().atZone(ZONE).toLocalDate()).isBefore(MONDAY);
        });
        assertThat(records.stream().filter(rec -> rec.direction() == Direction.IN)).hasSize(WORKDAYS * WORKERS.size());
        assertThat(records).isSortedAccordingTo((left, right) -> left.ts().compareTo(right.ts()));
    }

    @Test
    void generate_sameSeed_sameHistory() {
        // when
        List<PassRecord> first = generator.generate("cp", "zone", WORKERS, WEEK, 0.3, MONDAY, new Random(SEED));
        List<PassRecord> second = generator.generate("cp", "zone", WORKERS, WEEK, 0.3, MONDAY, new Random(SEED));

        // then
        assertThat(first).extracting(PassRecord::ts, PassRecord::workerId, PassRecord::decision, PassRecord::code)
                .containsExactlyElementsOf(second.stream().map(rec -> tuple(rec.ts(), rec.workerId(),
                        rec.decision(), rec.code())).toList());
    }

    @Test
    void generate_fullDenyRate_denyBeforeEachEntry() {
        // when
        List<PassRecord> records = generator.generate("cp", "zone", List.of("w1"), WEEK, 1, MONDAY, new Random(SEED));

        // then
        assertThat(records.stream().filter(rec -> rec.decision() == Decision.DENY)).hasSize(WORKDAYS);
    }
}
