package ru.proxodnaya.assistant.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.assistant.config.properties.AssistantProperties;

/**
 * HTTP-клиенты: к провайдеру LLM (таймаут FR-45) и к shift (токен пользователя пробрасывается в {@code ShiftClient}).
 */
@Configuration
public class HttpClientsConfig {

    private static final Duration CONNECT = Duration.ofSeconds(3);
    private static final Duration SHIFT_READ = Duration.ofSeconds(10);
    private static final String OLLAMA = "ollama";

    @Bean
    @Qualifier("llm")
    RestClient llmRestClient(RestClient.Builder builder, AssistantProperties props) {
        String base = props.baseUrl().replaceAll("/+$", "");
        String url = OLLAMA.equals(props.provider()) ? base + "/v1" : base;
        return builder.clone().baseUrl(url).requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(
                ClientHttpRequestFactorySettings.defaults().withConnectTimeout(CONNECT).withReadTimeout(props.timeout()))).build();
    }

    @Bean
    @Qualifier("shift")
    RestClient shiftRestClient(RestClient.Builder builder, AssistantProperties props) {
        return builder.clone().baseUrl(props.shiftUrl()).requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(
                ClientHttpRequestFactorySettings.defaults().withConnectTimeout(CONNECT).withReadTimeout(SHIFT_READ))).build();
    }
}
