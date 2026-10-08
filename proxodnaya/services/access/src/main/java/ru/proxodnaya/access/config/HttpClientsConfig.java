package ru.proxodnaya.access.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.config.properties.AccessProperties;

/**
 * HTTP-клиенты к face, people и shift с таймаутом из {@code access.deps.timeout}.
 */
@Configuration
public class HttpClientsConfig {

    @Bean(destroyMethod = "close")
    ExecutorService depsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @Qualifier("face")
    RestClient faceRestClient(RestClient.Builder builder, AccessProperties props) {
        return builder.clone().baseUrl(props.deps().faceUrl()).requestFactory(factory(props)).build();
    }

    @Bean
    @Qualifier("people")
    RestClient peopleRestClient(RestClient.Builder builder, AccessProperties props) {
        return builder.clone().baseUrl(props.deps().peopleUrl()).requestFactory(factory(props)).build();
    }

    @Bean
    @Qualifier("shift")
    RestClient shiftRestClient(RestClient.Builder builder, AccessProperties props) {
        return builder.clone().baseUrl(props.deps().shiftUrl()).requestFactory(factory(props)).build();
    }

    private static ClientHttpRequestFactory factory(AccessProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.deps().timeout())
                .withReadTimeout(props.deps().timeout());
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
