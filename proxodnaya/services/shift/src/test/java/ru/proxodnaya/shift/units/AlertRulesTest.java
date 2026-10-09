package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.shift.features.alert.command.service.AlertRules;
import ru.proxodnaya.shift.features.alert.command.service.Geofence;
import ru.proxodnaya.shift.features.alert.model.AlertCandidate;
import ru.proxodnaya.shift.features.alert.model.AlertType;
import ru.proxodnaya.shift.features.alert.model.OnSiteWorker;

class AlertRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");
    private static final Instant ENTERED = NOW.minusSeconds(3600);
    private static final AlertRules RULES = new AlertRules(true, Duration.ofMinutes(5));

    @Test
    void evaluate_outsideFence_silent_neverSent() {
        // given
        List<OnSiteWorker> workers = List.of(
                new OnSiteWorker("out", "z1", ENTERED, NOW.minusSeconds(30), false, 950d),
                new OnSiteWorker("silent", "z1", ENTERED, NOW.minusSeconds(600), true, 10d),
                new OnSiteWorker("ok", "z1", ENTERED, NOW.minusSeconds(30), true, 10d),
                new OnSiteWorker("noapp", "z1", ENTERED, null, null, null));

        // when
        List<AlertCandidate> found = RULES.evaluate(workers, NOW);

        // then
        assertThat(found).extracting(AlertCandidate::key).containsExactly("LEFT_SITE:out", "NO_SIGNAL:silent");
        assertThat(found.getFirst().details()).contains("950 м");
        assertThat(found.getFirst().type()).isEqualTo(AlertType.LEFT_SITE);
    }

    @Test
    void evaluate_heartbeatBeforeEntry_notLeftSite_fenceOff_noLeftSite() {
        // given: последний heartbeat вне геозоны был до входа — по нему не судим
        OnSiteWorker old = new OnSiteWorker("w1", "z1", NOW.minusSeconds(60), NOW.minusSeconds(120), false, 900d);

        // when / then
        assertThat(RULES.evaluate(List.of(old), NOW)).isEmpty();
        assertThat(new AlertRules(false, Duration.ofMinutes(5))
                .evaluate(List.of(new OnSiteWorker("w2", "z1", ENTERED, NOW, false, 900d)), NOW)).isEmpty();
    }

    @Test
    void distance_moscowKremlinToRedSquare_hundredsOfMeters() {
        assertThat(Geofence.distance(55.7520, 37.6175, 55.7539, 37.6208)).isBetween(200d, 300d);
    }
}
