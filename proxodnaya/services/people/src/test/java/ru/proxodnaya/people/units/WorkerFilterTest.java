package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.people.features.worker.model.WorkerFilter;

class WorkerFilterTest {

    @Test
    void parse_knownValues_caseInsensitive() {
        assertThat(WorkerFilter.parse(null)).isEqualTo(WorkerFilter.ALL);
        assertThat(WorkerFilter.parse("blocked")).isEqualTo(WorkerFilter.BLOCKED);
        assertThat(WorkerFilter.parse("Active")).isEqualTo(WorkerFilter.ACTIVE);
    }

    @Test
    void parse_inside_isNotPeoplesBusiness() {
        assertThatThrownBy(() -> WorkerFilter.parse("inside")).isInstanceOf(DomainException.class);
    }
}
