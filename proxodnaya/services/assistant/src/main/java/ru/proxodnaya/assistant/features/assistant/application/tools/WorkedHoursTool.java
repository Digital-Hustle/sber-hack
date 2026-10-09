package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.proxodnaya.assistant.config.properties.AssistantProperties;
import ru.proxodnaya.assistant.gateway.ShiftClient;
import ru.proxodnaya.assistant.llm.ToolSpec;

/**
 * Отработанные часы по людям за период (по интервалам присутствия).
 */
@Component
public class WorkedHoursTool implements AssistantTool {

    static final String NAME = "worked_hours";
    private static final String WORKER_ID = "workerId";
    private static final String ITEMS = "items";

    private final ShiftClient shift;
    private final AssistantProperties props;
    private final Clock clock;

    public WorkedHoursTool(ShiftClient shift, AssistantProperties props, Clock clock) {
        this.shift = shift;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Отработанные часы по людям за период: workedHours, число интервалов, неполные (выход не отмечен)",
                Period.schema(Map.of(WORKER_ID, Map.of("type", "string", "description", "id рабочего, если вопрос про одного"))));
    }

    @Override
    public JsonNode call(JsonNode args) {
        Period period = Period.parse(args, props.siteZone(), clock);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("from", period.start().toString());
        params.put("to", period.end().toString());
        params.put("worker", Tools.text(args, WORKER_ID));
        JsonNode result = shift.get("/analytics/hours", params);
        long total = 0;
        for (JsonNode item : result.path(ITEMS)) {
            long minutes = item.path("workedMinutes").asLong();
            total += minutes;
            if (item instanceof ObjectNode node) {
                node.put("workedHours", Numbers.hours(minutes));
            }
        }
        if (result instanceof ObjectNode node) {
            node.put("totalWorkedHours", Numbers.hours(total));
            node.put("people", result.path(ITEMS).size());
        }
        return result;
    }
}
