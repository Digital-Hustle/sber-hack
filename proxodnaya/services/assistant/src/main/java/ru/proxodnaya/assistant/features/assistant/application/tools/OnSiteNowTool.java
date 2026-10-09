package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.assistant.gateway.ShiftClient;
import ru.proxodnaya.assistant.features.assistant.model.Table;
import ru.proxodnaya.assistant.llm.ToolSpec;

/**
 * Сколько людей на объекте сейчас, по зонам, проходы за сегодня.
 */
@Component
public class OnSiteNowTool implements AssistantTool {

    static final String NAME = "on_site_now";

    private final ShiftClient shift;

    public OnSiteNowTool(ShiftClient shift) {
        this.shift = shift;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Сколько людей сейчас на объекте и по зонам, неполные интервалы, проходы за сегодня "
                + "(пропущено, вручную, отказано)", Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public JsonNode call(JsonNode args) {
        return shift.get("/analytics/summary", Map.of());
    }

    @Override
    public Optional<Table> table(JsonNode result) {
        return AssistantTool.rows(NAME, result.path("byZone"), List.of("zoneId", "inside"));
    }
}
