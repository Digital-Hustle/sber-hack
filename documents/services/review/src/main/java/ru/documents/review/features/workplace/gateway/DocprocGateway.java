package ru.documents.review.features.workplace.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.documents.common.exception.DomainException;
import ru.documents.common.security.ServiceTokenProvider;

/**
 * Шлюз docproc: результат разбора, смена вида, разобрать заново (service account svc-review).
 */
@Component
public class DocprocGateway {

    private static final String AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public DocprocGateway(@Qualifier("docprocRestClient") RestClient http, ServiceTokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /**
     * Последний результат документа.
     *
     * @param documentId документ
     * @return результат или пусто, если разбора ещё не было
     */
    public Optional<JsonNode> result(UUID documentId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/docproc/documents/{id}/result", documentId)
                    .header(AUTH, BEARER + tokens.token()).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    /**
     * Сменить вид → переизвлечение по новой схеме.
     *
     * @param documentId документ
     * @param type       вид
     */
    public void retype(UUID documentId, String type) {
        call(() -> http.post().uri("/internal/docproc/documents/{id}/retype", documentId)
                .header(AUTH, BEARER + tokens.token()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", type)).retrieve().toBodilessEntity());
    }

    /**
     * Разобрать заново.
     *
     * @param documentId документ
     */
    public void reprocess(UUID documentId) {
        call(() -> http.post().uri("/internal/docproc/documents/{id}/reprocess", documentId)
                .header(AUTH, BEARER + tokens.token()).retrieve().toBodilessEntity());
    }

    private void call(Runnable r) {
        try {
            r.run();
        } catch (HttpClientErrorException.NotFound e) {
            throw new DomainException("NOT_PARSED_YET", 409, "Документ ещё не поступил в разбор",
                    "Подождите, пока документ появится в очереди разбора");
        } catch (HttpClientErrorException e) {
            throw DomainException.badRequest("TYPE_UNKNOWN", "Нет такого вида документа", "Выберите вид из списка");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException("DOCPROC_UNAVAILABLE", 503, "Сервис разбора недоступен", "Повторите через минуту");
    }
}
