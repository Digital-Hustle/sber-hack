package ru.documents.docproc.features.processing.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Версионированные промпты из resources/prompts (FR-45); версия попадает в результат.
 */
@Component
public class Prompts {

    public static final String CLASSIFY = "classify.v1";
    public static final String EXTRACT = "extract.v1";

    /**
     * Текст шаблона с подстановками {@code {{key}}}.
     *
     * @param name имя файла без .md
     * @param vars подстановки
     * @return текст
     */
    public String render(String name, Map<String, String> vars) {
        String s = load(name);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return s;
    }

    private String load(String name) {
        try (InputStream in = Prompts.class.getResourceAsStream("/prompts/" + name + ".md")) {
            if (in == null) {
                throw new IllegalStateException("prompt " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
