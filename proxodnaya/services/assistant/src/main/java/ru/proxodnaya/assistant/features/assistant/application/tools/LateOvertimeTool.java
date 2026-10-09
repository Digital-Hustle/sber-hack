package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.proxodnaya.assistant.config.properties.AssistantProperties;
import ru.proxodnaya.assistant.gateway.ShiftClient;
import ru.proxodnaya.assistant.features.assistant.model.Table;
import ru.proxodnaya.assistant.llm.ToolSpec;

/**
 * Опоздания, неявки и переработки по сменам за период.
 */
@Component
public class LateOvertimeTool implements AssistantTool {

    static final String NAME = "late_overtime";
    private static final String ABSENT = "absent";
    private static final String WORKER_ID = "workerId";
    private static final String ITEMS = "items";
    private static final String LATE = "late";
    private static final String OVERTIME = "overtime";

    private final ShiftClient shift;
    private final AssistantProperties props;
    private final Clock clock;

    public LateOvertimeTool(ShiftClient shift, AssistantProperties props, Clock clock) {
        this.shift = shift;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Опоздания, неявки и переработки по людям за период (по началу смены): число смен, опозданий, "
                + "минуты опозданий и переработок; totals — итоги",
                Period.schema(Map.of(WORKER_ID, Map.of("type", "string", "description", "id рабочего, если вопрос про одного"))));
    }

    @Override
    public JsonNode call(JsonNode args) {
        Period period = Period.parse(args, props.siteZone(), clock);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("from", period.start().toString());
        params.put("to", period.end().toString());
        params.put("worker", Tools.text(args, WORKER_ID));
        JsonNode result = shift.get("/analytics/late-overtime", params);
        if (result instanceof ObjectNode node) {
            node.remove("details");
            node.set("totals", totals(node));
        }
        return result;
    }

    private static ObjectNode totals(ObjectNode result) {
        int lateWorkers = 0;
        int overtimeWorkers = 0;
        long late = 0;
        long absent = 0;
        long overtime = 0;
        for (JsonNode item : result.path(ITEMS)) {
            late += item.path(LATE).asLong();
            absent += item.path(ABSENT).asLong();
            overtime += item.path(OVERTIME).asLong();
            lateWorkers += item.path(LATE).asLong() > 0 ? 1 : 0;
            overtimeWorkers += item.path(OVERTIME).asLong() > 0 ? 1 : 0;
        }
        ObjectNode totals = result.objectNode();
        totals.put("people", result.path(ITEMS).size());
        totals.put("lateShifts", late);
        totals.put("latePeople", lateWorkers);
        totals.put("absentShifts", absent);
        totals.put("overtimeShifts", overtime);
        totals.put("overtimePeople", overtimeWorkers);
        return totals;
    }

    @Override
    public Optional<Table> table(JsonNode result) {
        return AssistantTool.rows(NAME, result.path(ITEMS),
                List.of(WORKER_ID, "shifts", LATE, "lateMinutes", ABSENT, OVERTIME, "overtimeMinutes"));
    }
}
