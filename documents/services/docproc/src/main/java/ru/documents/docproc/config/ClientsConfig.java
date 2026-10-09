package ru.documents.docproc.config;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиенты зависимостей с тайм-аутами (intake, ocr).
 */
@Configuration
public class ClientsConfig {

    private static final Duration CONNECT = Duration.ofSeconds(3);

    /** Клиент intake. */
    @Bean
    public RestClient intakeRestClient(DocprocProperties props) {
        return client(props.intakeUrl(), Duration.ofSeconds(30));
    }

    /** Клиент ocr. */
    @Bean
    public RestClient ocrRestClient(DocprocProperties props) {
        return client(props.ocrUrl(), props.ocrTimeout());
    }

    private static RestClient client(String base, Duration read) {
        var settings = ClientHttpRequestFactorySettings.defaults().withConnectTimeout(CONNECT).withReadTimeout(read);
        var factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(b -> b.executor(Executors.newVirtualThreadPerTaskExecutor()))
                .build(settings);
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
}
