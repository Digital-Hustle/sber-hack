package ru.subsidy.docproc.text;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.docproc.config.DocprocProperties;

/**
 * Клиент ocr (Python): POST /v1/extract → текст страниц (текстовый слой или Tesseract).
 */
@Component
public class OcrClient {

    private final RestClient http;
    private final boolean enabled;

    /**
     * Конструктор.
     *
     * @param props настройки
     */
    public OcrClient(DocprocProperties props) {
        this.enabled = props.ocrUrl() != null && !props.ocrUrl().isBlank();
        this.http = RestClient.builder().baseUrl(enabled ? props.ocrUrl() : "http://localhost").build();
    }

    /**
     * Включён ли OCR.
     *
     * @return true, если задан OCR_URL
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Распознаёт файл.
     *
     * @param content файл
     * @param mime тип
     * @return страницы
     */
    public List<PageText> extract(byte[] content, String mime) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "document";
            }
        }).contentType(MediaType.parseMediaType(mime));
        JsonNode body = http.post().uri("/v1/extract").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form.build()).retrieve().body(JsonNode.class);
        List<PageText> pages = new ArrayList<>();
        if (body != null) {
            for (JsonNode page : body.path("pages")) {
                String text = page.path("text").asText("");
                pages.add(new PageText(page.path("n").asInt(pages.size() + 1), text,
                        text.isBlank() ? PageText.NONE : PageText.OCR));
            }
        }
        return pages;
    }
}
