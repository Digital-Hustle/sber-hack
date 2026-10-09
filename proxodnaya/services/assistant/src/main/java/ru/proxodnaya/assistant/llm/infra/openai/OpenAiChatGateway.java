package ru.proxodnaya.assistant.llm.infra.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.proxodnaya.assistant.config.properties.AssistantProperties;
import ru.proxodnaya.assistant.llm.ChatMessage;
import ru.proxodnaya.assistant.llm.ChatRq;
import ru.proxodnaya.assistant.llm.ChatRs;
import ru.proxodnaya.assistant.llm.LlmGateway;
import ru.proxodnaya.assistant.llm.LlmUnavailableException;
import ru.proxodnaya.assistant.llm.ToolCall;
import ru.proxodnaya.assistant.llm.ToolSpec;

/**
 * OpenAI-совместимый {@code /chat/completions}: OpenAI, vLLM и Ollama ({@code /v1}). Ответ без инструментов — текст.
 */
@Component
public class OpenAiChatGateway implements LlmGateway {

    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final String FUNCTION = "function";
    private static final String CONTENT = "content";
    private static final String TOOL_CALLS = "tool_calls";
    private static final String NAME = "name";
    private static final String ID = "id";
    private static final String ARGUMENTS = "arguments";
    private static final String TYPE = "type";

    private final RestClient http;
    private final ObjectMapper json;
    private final AssistantProperties props;

    public OpenAiChatGateway(@Qualifier("llm") RestClient http, ObjectMapper json, AssistantProperties props) {
        this.http = http;
        this.json = json;
        this.props = props;
    }

    @Override
    public ChatRs chat(ChatRq request) {
        JsonNode body;
        try {
            RestClient.RequestBodySpec spec = http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON);
            if (props.apiKey() != null && !props.apiKey().isBlank()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
            }
            body = spec.body(payload(request)).retrieve().body(JsonNode.class);
        } catch (RestClientException error) {
            throw new LlmUnavailableException("LLM не ответила: " + error.getMessage(), error);
        }
        JsonNode message = body == null ? null : body.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) {
            throw new LlmUnavailableException("LLM ответила не по формату", null);
        }
        String text = THINK.matcher(message.path(CONTENT).asText("")).replaceAll("").strip();
        JsonNode usage = body.path("usage");
        return new ChatRs(text, calls(message.path(TOOL_CALLS)), tokens(usage, "prompt_tokens"), tokens(usage, "completion_tokens"));
    }

    private static List<ToolCall> calls(JsonNode toolCalls) {
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : toolCalls) {
            JsonNode function = call.path(FUNCTION);
            String name = function.path(NAME).asText();
            JsonNode args = function.path(ARGUMENTS);
            calls.add(new ToolCall(call.path(ID).asText(name), name, args.isTextual() ? args.asText() : args.toString()));
        }
        return calls;
    }

    private static Integer tokens(JsonNode usage, String field) {
        return usage.has(field) ? usage.get(field).asInt() : null;
    }

    @Override
    public String provider() {
        return props.provider();
    }

    @Override
    public String model() {
        return props.model();
    }

    private ObjectNode payload(ChatRq request) {
        ObjectNode root = json.createObjectNode();
        root.put("model", props.model());
        root.put("temperature", 0);
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role());
            node.put(CONTENT, message.content() == null ? "" : message.content());
            if (message.toolCallId() != null) {
                node.put("tool_call_id", message.toolCallId());
            }
            if (!message.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray(TOOL_CALLS);
                for (ToolCall call : message.toolCalls()) {
                    ObjectNode item = calls.addObject();
                    item.put(ID, call.id());
                    item.put(TYPE, FUNCTION);
                    item.putObject(FUNCTION).put(NAME, call.name()).put(ARGUMENTS, call.arguments());
                }
            }
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolSpec tool : request.tools()) {
                ObjectNode function = tools.addObject().put(TYPE, FUNCTION).putObject(FUNCTION);
                function.put(NAME, tool.name()).put("description", tool.description());
                function.set("parameters", json.valueToTree(tool.parameters()));
            }
        }
        return root;
    }
}
