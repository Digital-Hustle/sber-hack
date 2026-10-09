package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Период инструмента: даты {@code from}..{@code to} включительно в поясе площадки → [начало from, начало to + 1).
 *
 * @param from  первый день
 * @param to    последний день
 * @param start начало, UTC
 * @param end   конец, не включая
 */
public record Period(LocalDate from, LocalDate to, Instant start, Instant end) {

    /** Как в shift: период аналитики не длиннее 93 дней. */
    public static final int MAX_DAYS = 93;
    private static final String DATE = "date";
    private static final String STRING = "string";
    private static final String DESCRIPTION = "description";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String TYPE = "type";
    private static final String FORMAT = "format";

    /**
     * Разбирает период; без дат — сегодня.
     *
     * @param args  аргументы
     * @param zone  пояс площадки
     * @param clock часы
     * @return период
     * @throws ToolArgumentException если даты не ISO, перепутаны или период длиннее 93 дней
     */
    public static Period parse(JsonNode args, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = date(args, FROM, today);
        LocalDate until = date(args, TO, from.isAfter(today) ? from : today);
        if (until.isBefore(from)) {
            throw new ToolArgumentException("to раньше from");
        }
        if (ChronoUnit.DAYS.between(from, until) >= MAX_DAYS) {
            throw new ToolArgumentException("период длиннее " + MAX_DAYS + " дней");
        }
        return new Period(from, until, from.atStartOfDay(zone).toInstant(), until.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * JSON-схема периода (и необязательные поля сверху).
     *
     * @param extra дополнительные свойства
     * @return схема
     */
    public static Map<String, Object> schema(Map<String, Object> extra) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(FROM, Map.of(TYPE, STRING, FORMAT, DATE, DESCRIPTION, "первый день периода, YYYY-MM-DD; по умолчанию сегодня"));
        props.put(TO, Map.of(TYPE, STRING, FORMAT, DATE, DESCRIPTION, "последний день включительно, YYYY-MM-DD"));
        props.putAll(extra);
        return Map.of(TYPE, "object", "properties", props, "required", List.of());
    }

    private static LocalDate date(JsonNode args, String name, LocalDate fallback) {
        JsonNode value = args == null ? null : args.get(name);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.asText().strip());
        } catch (DateTimeParseException error) {
            throw new ToolArgumentException(name + " — не дата YYYY-MM-DD");
        }
    }
}
