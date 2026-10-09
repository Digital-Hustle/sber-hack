package ru.documents.llm.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.documents.llm.api.LlmRequest;
import ru.documents.llm.core.LlmProperties;
import ru.documents.llm.core.LlmProvider;
import ru.documents.llm.infra.gigachat.GigaChatAdapter;
import ru.documents.llm.infra.ollama.OllamaAdapter;
import ru.documents.llm.infra.openai.OpenAiCompatAdapter;

/** LLM-02/03/06: формат запросов провайдеров, OAuth GigaChat, vision, ошибки → IOException (повод для WAITING_MODEL). */
class AdaptersTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpSupport HTTP = new HttpSupport(JSON, Duration.ofSeconds(5), null);

    private static JsonNode schema() throws IOException {
        return JSON.readTree("{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\"}},\"required\":[\"type\"]}");
    }

    private static LlmProperties props(String provider, String base) {
        return new LlmProperties(provider, base, "text-model", "vision-model", "secret-key", Duration.ofSeconds(5), 1, 0,
                "json_schema", "basic-auth-key", "GIGACHAT_API_PERS", base + "/oauth", null);
    }

    private static LlmRequest rq(List<byte[]> images) throws IOException {
        return new LlmRequest("classify", "doc-1", "системный", "текст документа", schema(), images);
    }

    @Test
    void ollamaSendsSchemaFormatImagesAndReadsUsage() throws Exception {
        try (StubServer s = new StubServer()) {
            s.reply("/api/chat", 200, "{\"message\":{\"content\":\"{\\\"type\\\":\\\"SNILS\\\"}\"},"
                    + "\"prompt_eval_count\":120,\"eval_count\":7}");
            LlmProvider p = new OllamaAdapter(HTTP, props("ollama", s.url()));
            LlmProvider.Completion c = p.complete(rq(List.of(new byte[] {1, 2, 3})), "vision-model", null);
            assertThat(c.text()).contains("SNILS");
            assertThat(c.tokensIn()).isEqualTo(120);
            JsonNode body = JSON.readTree(s.calls().get(0).body());
            assertThat(body.path("model").asText()).isEqualTo("vision-model");
            assertThat(body.path("format").path("required").get(0).asText()).isEqualTo("type");
            assertThat(body.path("stream").asBoolean()).isFalse();
            assertThat(body.path("options").path("temperature").asInt()).isZero();
            assertThat(body.path("messages").get(1).path("images").get(0).asText()).isEqualTo("AQID");
        }
    }

    @Test
    void openAiSendsBearerJsonSchemaAndImageParts() throws Exception {
        try (StubServer s = new StubServer()) {
            s.reply("/chat/completions", 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"type\\\":\\\"INN\\\"}\"}}],"
                    + "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":5}}");
            LlmProvider p = new OpenAiCompatAdapter(HTTP, props("openai", s.url()));
            LlmProvider.Completion c = p.complete(rq(List.of(new byte[] {1})), "vision-model", "Ошибка схемы");
            assertThat(c.text()).contains("INN");
            assertThat(c.tokensOut()).isEqualTo(5);
            StubServer.Call call = s.calls().get(0);
            assertThat(call.header("Authorization")).isEqualTo("Bearer secret-key");
            String body = call.body();
            assertThat(body).contains("json_schema").contains("image_url").contains("Ошибка схемы");
        }
    }

    @Test
    void gigaChatGetsOauthTokenOnceAndUsesIt() throws Exception {
        try (StubServer s = new StubServer()) {
            long exp = System.currentTimeMillis() + 30 * 60 * 1000;
            s.reply("/oauth", 200, "{\"access_token\":\"tok-1\",\"expires_at\":" + exp + "}");
            s.reply("/chat/completions", 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"type\\\":\\\"MEDICAL\\\"}\"}}]}");
            LlmProvider p = new GigaChatAdapter(HTTP, props("gigachat", s.url()), Clock.systemUTC());
            p.complete(rq(List.of()), "GigaChat-2", null);
            p.complete(rq(List.of()), "GigaChat-2", null);
            List<StubServer.Call> oauth = s.calls().stream().filter(c -> c.path().equals("/oauth")).toList();
            assertThat(oauth).hasSize(1);
            assertThat(oauth.get(0).header("Authorization")).isEqualTo("Basic basic-auth-key");
            assertThat(oauth.get(0).header("RqUID")).isNotBlank();
            assertThat(oauth.get(0).body()).isEqualTo("scope=GIGACHAT_API_PERS");
            StubServer.Call chat = s.calls().stream().filter(c -> c.path().equals("/chat/completions")).findFirst().orElseThrow();
            assertThat(chat.header("Authorization")).isEqualTo("Bearer tok-1");
        }
    }

    @Test
    void serverErrorAndDownServerAreIoExceptions() throws Exception {
        try (StubServer s = new StubServer()) {
            s.reply("/api/chat", 503, "{\"error\":\"loading\"}");
            LlmProvider p = new OllamaAdapter(HTTP, props("ollama", s.url()));
            assertThatThrownBy(() -> p.complete(rq(List.of()), "text-model", null)).isInstanceOf(IOException.class);
        }
        LlmProvider down = new OllamaAdapter(HTTP, props("ollama", "http://127.0.0.1:1"));
        assertThatThrownBy(down::ping).isInstanceOf(IOException.class);
    }
}
