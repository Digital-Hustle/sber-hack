package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.pass.model.Presence;
import ru.proxodnaya.access.shared.Direction;

class PresenceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final Duration TTL = Duration.ofHours(16);

    @Test
    void nextDirection_insideRecently_out() {
        // when / then
        assertThat(new Presence(true, NOW.minus(Duration.ofHours(8))).nextDirection(NOW, TTL)).isEqualTo(Direction.OUT);
    }

    @Test
    void nextDirection_insideOlderThanTtl_inSoForgottenExitDoesNotLockOut() {
        // when / then
        assertThat(new Presence(true, NOW.minus(Duration.ofHours(17))).nextDirection(NOW, TTL)).isEqualTo(Direction.IN);
    }

    @Test
    void nextDirection_outside_in() {
        // when / then
        assertThat(Presence.outside().nextDirection(NOW, TTL)).isEqualTo(Direction.IN);
    }
}
