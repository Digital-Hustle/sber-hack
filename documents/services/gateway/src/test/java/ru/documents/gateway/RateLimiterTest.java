package ru.documents.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ru.documents.gateway.limit.RateLimiter;

class RateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void limitThenRetryAfterThenNewWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter l = new RateLimiter(clock);
        assertThat(l.acquire("k", 2, Duration.ofMinutes(1))).isZero();
        assertThat(l.acquire("k", 2, Duration.ofMinutes(1))).isZero();
        assertThat(l.acquire("k", 2, Duration.ofMinutes(1))).isBetween(1L, 61L);
        assertThat(l.acquire("other", 2, Duration.ofMinutes(1))).isZero();
        clock.now = clock.now.plusSeconds(61);
        assertThat(l.acquire("k", 2, Duration.ofMinutes(1))).isZero();
    }
}
