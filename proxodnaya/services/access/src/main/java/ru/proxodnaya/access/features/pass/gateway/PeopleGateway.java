package ru.proxodnaya.access.features.pass.gateway;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.features.pass.model.WorkerProfile;

/**
 * Сервис people: {@code GET /internal/people/workers/{id}/access-profile}. 404 — рабочего нет.
 */
@Component
public class PeopleGateway {

    private final RestClient client;

    public PeopleGateway(@Qualifier("people") RestClient client) {
        this.client = client;
    }

    /**
     * Профиль доступа.
     *
     * @param workerId рабочий
     * @return профиль или пусто, если рабочий не найден
     */
    public Optional<WorkerProfile> accessProfile(String workerId) {
        ProfileRs body = client.get()
                .uri("/internal/people/workers/{id}/access-profile", workerId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("people ответил " + response.getStatusCode().value());
                    }
                    return Objects.requireNonNull(response.bodyTo(ProfileRs.class), "people вернул пустой ответ");
                });
        if (body == null) {
            return Optional.empty();
        }
        LocalDate permitUntil = body.permits() == null ? null : body.permits().stream()
                .map(PermitRs::validUntil)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return Optional.of(new WorkerProfile(workerId, body.status(), body.fullName(), body.position(), body.contractor(),
                body.deviceIds() == null ? Set.of() : Set.copyOf(body.deviceIds()), permitUntil,
                body.zoneIds() == null ? Set.of() : Set.copyOf(body.zoneIds())));
    }

    private record ProfileRs(String status, String fullName, String position, String contractor,
                             List<String> deviceIds, List<PermitRs> permits, List<String> zoneIds) {
    }

    private record PermitRs(String type, LocalDate validUntil) {
    }
}
