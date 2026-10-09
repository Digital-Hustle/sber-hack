package ru.documents.review.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиенты зависимостей с тайм-аутами (intake, docproc).
 */
@Configuration
public class ClientsConfig {

    private static final Duration CONNECT = Duration.ofSeconds(3);
    private static final Duration READ = Duration.ofSeconds(10);

    /** Клиент intake. */
    @Bean
    public RestClient intakeRestClient(ReviewProperties props) {
        return client(props.intakeUrl());
    }

    /** Клиент docproc. */
    @Bean
    public RestClient docprocRestClient(ReviewProperties props) {
        return client(props.docprocUrl());
    }

    private static RestClient client(String base) {
        var settings = ClientHttpRequestFactorySettings.defaults().withConnectTimeout(CONNECT).withReadTimeout(READ);
        var factory = ClientHttpRequestFactoryBuilder.jdk()
                // HTTP/1.1: по умолчанию JDK-клиент шлёт h2c-upgrade, uvicorn (ocr) отвечает на него 400 text/plain (X-07).
                .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1)
                        .executor(Executors.newVirtualThreadPerTaskExecutor()))
                .build(settings);
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
}
