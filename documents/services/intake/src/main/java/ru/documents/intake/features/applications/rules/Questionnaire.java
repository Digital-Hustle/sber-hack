package ru.documents.intake.features.applications.rules;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.documents.common.exception.DomainException;

/**
 * Вопросы анкеты (FR-10) и проверка ответов на сервере.
 */
@Component
public class Questionnaire {

    /**
     * Вопрос.
     *
     * @param id       ключ ответа
     * @param text     текст
     * @param kind     single | bool
     * @param options  варианты
     * @param showWhen показывать, если ответ на другой вопрос такой (или null)
     */
    public record Question(String id, String text, String kind, List<Option> options, Map<String, List<String>> showWhen) {
    }

    /**
     * Вариант ответа.
     *
     * @param value значение
     * @param label подпись
     * @param hint  пояснение
     */
    public record Option(String value, String label, String hint) {
    }

    private static final String CITIZENSHIP = "citizenship";
    private static final String BOOL = "bool";

    private final List<Question> questions = List.of(
            new Question(CITIZENSHIP, "Какое у вас гражданство?", "single", List.of(
                    new Option("RF", "Россия", null),
                    new Option("EAEU", "Страна ЕАЭС", "Армения, Беларусь, Казахстан, Кыргызстан"),
                    new Option("VISA_FREE", "Другая страна, безвизовый въезд", "Например, Узбекистан, Таджикистан")),
                    null),
            new Question("medicalRequired", "Должность требует медосмотра?", BOOL, List.of(), Map.of(CITIZENSHIP,
                    List.of("RF"))),
            new Question("hasInn", "У вас уже есть ИНН?", BOOL, List.of(), Map.of(CITIZENSHIP,
                    List.of("EAEU", "VISA_FREE"))),
            new Question("hasSnils", "У вас уже есть СНИЛС?", BOOL, List.of(), Map.of(CITIZENSHIP,
                    List.of("VISA_FREE"))));

    /**
     * Все вопросы.
     *
     * @return вопросы
     */
    public List<Question> questions() {
        return questions;
    }

    /**
     * Проверить ответы.
     *
     * @param answers ответы
     */
    public void validate(Map<String, Object> answers) {
        Object c = answers.get(CITIZENSHIP);
        if (c == null || questions.get(0).options().stream().noneMatch(o -> o.value().equals(c))) {
            throw new DomainException("VALIDATION_ERROR", 400, "Укажите гражданство", "Выберите один из вариантов");
        }
        for (Question q : questions) {
            Object a = answers.get(q.id());
            if (a != null && BOOL.equals(q.kind()) && !(a instanceof Boolean)) {
                throw new DomainException("VALIDATION_ERROR", 400, "Неверный ответ: " + q.text(), "Ответьте да или нет");
            }
        }
    }
}
