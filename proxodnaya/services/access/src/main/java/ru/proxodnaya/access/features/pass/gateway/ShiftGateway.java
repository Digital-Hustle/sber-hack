package ru.proxodnaya.access.features.pass.gateway;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.features.pass.model.ActiveShift;

/**
 * Сервис shift: {@code GET /internal/shift/workers/{id}/active-shift?at=}. 404/204 — смены нет.
 */
@Component
public class ShiftGateway {

    private final RestClient client;

    public ShiftGateway(@Qualifier("shift") RestClient client) {
        this.client = client;
    }

    /**
     * Активная смена.
     *
     * @param workerId рабочий
     * @param moment       момент прохода
     * @return смена или пусто
     */
    public Optional<ActiveShift> activeShift(String workerId, Instant moment) {
        ActiveShift shift = client.get()
                .uri(uri -> uri.path("/internal/shift/workers/{id}/active-shift").queryParam("at", moment.toString()).build(workerId))
                .exchange((request, response) -> {
                    boolean none = response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                            || response.getStatusCode().isSameCodeAs(HttpStatus.NO_CONTENT);
                    if (none) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("shift ответил " + response.getStatusCode().value());
                    }
                    return response.bodyTo(ActiveShift.class);
                });
        return Optional.ofNullable(shift);
    }
}
