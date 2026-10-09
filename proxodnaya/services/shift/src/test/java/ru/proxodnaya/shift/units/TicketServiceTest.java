package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.shift.config.properties.StreamProperties;
import ru.proxodnaya.shift.features.stream.command.service.TicketService;
import ru.proxodnaya.shift.features.stream.model.StreamKind;
import ru.proxodnaya.shift.features.stream.model.StreamTicket;

class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    private static final StreamProperties PROPS = new StreamProperties(Duration.ofSeconds(30), Duration.ofSeconds(15),
            Duration.ofMinutes(30), 10);

    @Test
    void redeem_onceForItsStream() {
        // given
        TicketService service = new TicketService(PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        StreamTicket ticket = service.issue(StreamKind.WORKER, "w1");

        // when
        StreamTicket redeemed = service.redeem(ticket.value(), StreamKind.WORKER);

        // then
        assertThat(redeemed.subject()).isEqualTo("w1");
        assertThat(ticket.value()).hasSizeGreaterThanOrEqualTo(40);
        assertThatThrownBy(() -> service.redeem(ticket.value(), StreamKind.WORKER)).isInstanceOf(DomainException.class);
    }

    @Test
    void redeem_wrongStreamOrExpired_rejected() {
        // given
        TicketService early = new TicketService(PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
        StreamTicket worker = early.issue(StreamKind.WORKER, "w1");

        // then
        assertThatThrownBy(() -> early.redeem(worker.value(), StreamKind.SITUATION)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> early.redeem(null, StreamKind.SITUATION)).isInstanceOf(DomainException.class);
    }

    @Test
    void redeem_afterTtl_rejected() {
        // given
        MutableClock clock = new MutableClock(NOW);
        TicketService service = new TicketService(PROPS, clock);
        StreamTicket ticket = service.issue(StreamKind.SITUATION, "m1");

        // when
        clock.now = NOW.plusSeconds(31);

        // then
        assertThatThrownBy(() -> service.redeem(ticket.value(), StreamKind.SITUATION)).isInstanceOf(DomainException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
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
}
