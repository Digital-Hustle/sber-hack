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
 * Отказы на входе по причинам: системные и правомерные.
 */
@Component
public class RefusalsTool implements AssistantTool {

    static final String NAME = "refusals";
    private static final String CHECKPOINT = "checkpoint";
    private static final String SHARE = "share";

    private final ShiftClient shift;
    private final AssistantProperties props;
    private final Clock clock;

    public RefusalsTool(ShiftClient shift, AssistantProperties props, Clock clock) {
        this.shift = shift;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Отказы на входе за период: попытки, отказы, доля в процентах, по причинам (SYSTEM — сбой системы, "
                + "LEGITIMATE — правомерный отказ)",
                Period.schema(Map.of(CHECKPOINT, Map.of("type", "string", "description", "id проходной, если вопрос про одну"))));
    }

    @Override
    public JsonNode call(JsonNode args) {
        Period period = Period.parse(args, props.siteZone(), clock);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("from", period.start().toString());
        params.put("to", period.end().toString());
        params.put(CHECKPOINT, Tools.text(args, CHECKPOINT));
        JsonNode result = shift.get("/analytics/refusals", params);
        if (result instanceof ObjectNode node) {
            node.put("deniedPercent", Numbers.percent(node.path("deniedShare").asDouble()));
            for (JsonNode reason : node.path("byReason")) {
                if (reason instanceof ObjectNode item) {
                    item.put("percent", Numbers.percent(item.path(SHARE).asDouble()));
                }
            }
        }
        return result;
    }
}
