package ru.proxodnaya.access.features.kiosk.gateway;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.features.kiosk.model.CheckpointView;

/**
 * Проходная терминала из people: {@code GET /internal/people/checkpoints/{id}}. 404 — проходной нет (или удалена).
 */
@Component
public class CheckpointGateway {

    private final RestClient client;

    public CheckpointGateway(@Qualifier("people") RestClient client) {
        this.client = client;
    }

    /**
     * Проходная.
     *
     * @param checkpointId id
     * @return проходная или пусто
     * @throws IllegalStateException если people ответил ошибкой
     */
    public Optional<CheckpointView> find(String checkpointId) {
        return Optional.ofNullable(client.get()
                .uri("/internal/people/checkpoints/{id}", checkpointId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                            || response.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("people ответил " + response.getStatusCode().value());
                    }
                    return Objects.requireNonNull(response.bodyTo(CheckpointView.class), "people вернул пустой ответ");
                }));
    }
}
