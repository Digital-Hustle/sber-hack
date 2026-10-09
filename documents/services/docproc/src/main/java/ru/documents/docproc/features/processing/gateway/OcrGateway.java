package ru.documents.docproc.features.processing.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.documents.common.security.ServiceTokenProvider;

/**
 * Сервис ocr: текст, слова с bbox, качество, MRZ; картинка страницы для vision-пути.
 */
@Component
public class OcrGateway {

    private static final String CODE = "OCR_UNAVAILABLE";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public OcrGateway(@Qualifier("ocrRestClient") RestClient http, ServiceTokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /**
     * Распознать файл.
     *
     * @param bytes файл
     * @return ответ /v1/extract
     * @throws OcrRejectedException файл повреждён по мнению ocr (4xx)
     */
    public JsonNode extract(byte[] bytes) {
        try {
            return http.post().uri("/v1/extract").header("Authorization", "Bearer " + tokens.token())
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form(bytes)).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new DependencyException("OCR_UNAUTHORIZED", e);
            }
            throw new OcrRejectedException(e.getResponseBodyAs(JsonNode.class));
        } catch (RestClientException e) {
            throw new DependencyException(CODE, e);
        }
    }

    /**
     * PNG страницы (те же координаты, что bbox).
     *
     * @param bytes файл
     * @param page  страница с 1
     * @return PNG
     */
    public byte[] render(byte[] bytes, int page) {
        try {
            byte[] b = http.post().uri("/v1/render?page={p}", page).header("Authorization", "Bearer " + tokens.token())
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form(bytes)).retrieve().body(byte[].class);
            return b == null ? new byte[0] : b;
        } catch (RestClientException e) {
            throw new DependencyException(CODE, e);
        }
    }

    private static MultiValueMap<String, Object> form(byte[] bytes) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "document";
            }
        });
        return form;
    }
}
