package ru.documents.intake.shared;

import java.util.Map;

import ru.documents.common.exception.DomainException;

/**
 * Коды отказов приёма и тексты стадий (docs/API.md). Один источник текста для API и UI.
 */
public final class Codes {

    private static final Map<String, String[]> STAGES = Map.ofEntries(
            Map.entry("RECEIVED", new String[] {"Файл принят", null}),
            Map.entry("QUEUED", new String[] {"В очереди на разбор", null}),
            Map.entry("EXTRACTING_TEXT", new String[] {"Читаем текст", null}),
            Map.entry("CLASSIFYING", new String[] {"Определяем, что это за документ", null}),
            Map.entry("EXTRACTING_FIELDS", new String[] {"Извлекаем данные", null}),
            Map.entry("VALIDATING", new String[] {"Проверяем данные", null}),
            Map.entry("PARSED", new String[] {"Документ разобран", null}),
            Map.entry("NEEDS_REVIEW", new String[] {"Документ разобран, его проверит оператор", null}),
            Map.entry("UNREADABLE", new String[] {"Не удалось прочитать", "Загрузите более чёткий снимок"}),
            Map.entry("WAITING_MODEL", new String[] {"Разбор временно приостановлен, продолжим автоматически", null}),
            Map.entry("FAILED", new String[] {"Не удалось разобрать, оператор проверит вручную", null}));

    private static final Map<String, String[]> REASONS = Map.ofEntries(
            Map.entry("IMAGE_BLURRY", new String[] {"Снимок размыт", "Держите телефон неподвижно, наведите фокус"}),
            Map.entry("IMAGE_TOO_DARK", new String[] {"Слишком темно", "Снимите при хорошем свете, без вспышки"}),
            Map.entry("IMAGE_GLARE", new String[] {"Блик закрывает текст", "Наклоните документ, уберите блик"}),
            Map.entry("IMAGE_CUT_OFF", new String[] {"Документ обрезан", "Весь документ должен быть в кадре"}),
            Map.entry("NOT_A_DOCUMENT", new String[] {"На файле нет документа", "Загрузите фото или скан документа"}),
            Map.entry("TEXT_NOT_FOUND", new String[] {"Не удалось найти текст", "Загрузите более чёткий снимок"}),
            Map.entry("TYPE_UNKNOWN", new String[] {"Не удалось определить вид документа",
                "Оператор проверит вручную; можно указать пункт"}),
            Map.entry("TYPE_MOVED", new String[] {"Это другой документ — перенесли в нужный пункт", null}),
            Map.entry("DOCUMENT_EXPIRED", new String[] {"Срок действия документа истёк", "Загрузите действующий документ"}),
            Map.entry("CHECKSUM_FAILED", new String[] {"Номер не прошёл проверку", "Оператор сверит с оригиналом"}),
            Map.entry("MRZ_MISMATCH", new String[] {"Данные в машиночитаемой строке не совпадают с текстом",
                "Оператор сверит с оригиналом"}),
            Map.entry("MULTI_DOCUMENT", new String[] {"В одном файле несколько документов — разделили по пунктам",
                "Проверьте, что каждый документ попал в свой пункт"}),
            Map.entry("MODEL_UNAVAILABLE", new String[] {"Модель недоступна", "Продолжим автоматически"}),
            Map.entry("MODEL_INVALID_OUTPUT", new String[] {"Модель вернула ответ не по схеме",
                "Оператор проверит вручную"}));

    private Codes() {
    }

    /**
     * Текст стадии/причины для соискателя.
     *
     * @param stage  стадия
     * @param reason код причины или null
     * @return [message, hint]
     */
    public static String[] text(String stage, String reason) {
        if (reason != null && REASONS.containsKey(reason)) {
            return REASONS.get(reason);
        }
        return STAGES.getOrDefault(stage, new String[] {stage, null});
    }

    /**
     * Отказ приёма.
     *
     * @param code   код
     * @param status HTTP
     * @param detail текст
     * @param hint   что сделать
     * @return исключение
     */
    public static DomainException reject(String code, int status, String detail, String hint) {
        return new DomainException(code, status, detail, hint);
    }
}
