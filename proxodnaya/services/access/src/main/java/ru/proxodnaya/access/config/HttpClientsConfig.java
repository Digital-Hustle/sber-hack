package ru.proxodnaya.access.config;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.config.properties.AccessProperties;

/**
 * HTTP-клиенты к face, people и shift с таймаутом из {@code access.deps.timeout}.
 */
@Configuration
public class HttpClientsConfig {

    /** Регистрация client credentials в {@code spring.security.oauth2.client.registration}. */
    static final String REGISTRATION = "svc";
    /** Токен один на сервис, а не на охранника, чей запрос сейчас обрабатывается. */
    private static final Authentication SERVICE = UsernamePasswordAuthenticationToken.authenticated("svc-access", null, List.of());

    @Bean(destroyMethod = "close")
    ExecutorService depsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    OAuth2AuthorizedClientManager serviceTokenManager(ClientRegistrationRepository registrations,
                                                      OAuth2AuthorizedClientService clients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    OAuth2ClientHttpRequestInterceptor serviceTokenInterceptor(OAuth2AuthorizedClientManager serviceTokenManager) {
        OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(serviceTokenManager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION);
        interceptor.setPrincipalResolver(request -> SERVICE);
        return interceptor;
    }

    @Bean
    @Qualifier("face")
    RestClient faceRestClient(RestClient.Builder builder, AccessProperties props, OAuth2ClientHttpRequestInterceptor token) {
        return client(builder, props.deps().faceUrl(), props, token);
    }

    @Bean
    @Qualifier("people")
    RestClient peopleRestClient(RestClient.Builder builder, AccessProperties props, OAuth2ClientHttpRequestInterceptor token) {
        return client(builder, props.deps().peopleUrl(), props, token);
    }

    @Bean
    @Qualifier("shift")
    RestClient shiftRestClient(RestClient.Builder builder, AccessProperties props, OAuth2ClientHttpRequestInterceptor token) {
        return client(builder, props.deps().shiftUrl(), props, token);
    }

    private static RestClient client(RestClient.Builder builder, String url, AccessProperties props,
                                     OAuth2ClientHttpRequestInterceptor token) {
        return builder.clone().baseUrl(url).requestFactory(factory(props)).requestInterceptor(token).build();
    }

    private static ClientHttpRequestFactory factory(AccessProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.deps().timeout())
                .withReadTimeout(props.deps().timeout());
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
