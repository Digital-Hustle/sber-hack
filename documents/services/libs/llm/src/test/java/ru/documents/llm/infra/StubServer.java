package ru.documents.llm.infra;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/** Мини-HTTP-сервер вместо WireMock: заданные ответы по пути и журнал запросов. */
public final class StubServer implements AutoCloseable {

    /** Записанный запрос. */
    public record Call(String method, String path, Map<String, List<String>> headers, String body) {
        /** Первый заголовок. */
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0)).findFirst().orElse(null);
        }
    }

    private record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(new Call(ex.getRequestMethod(), ex.getRequestURI().getPath(), Map.copyOf(ex.getRequestHeaders()), body));
            Reply r = replies.getOrDefault(ex.getRequestURI().getPath(), new Reply(404, "{}"));
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(r.status(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    public StubServer reply(String path, int status, String body) {
        replies.put(path, new Reply(status, body));
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Call> calls() {
        return calls;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
