package ru.proxodnaya.people.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.people.config.properties.PeopleProperties;

/**
 * HTTP-клиент к face с токеном сервиса (client credentials {@code svc-people}).
 */
@Configuration
public class HttpClientsConfig {

    /** Регистрация client credentials в {@code spring.security.oauth2.client.registration}. */
    static final String REGISTRATION = "svc";
    /** Токен один на сервис, а не на администратора, чей запрос сейчас обрабатывается. */
    private static final Authentication SERVICE = UsernamePasswordAuthenticationToken.authenticated("svc-people", null, List.of());

    @Bean
    OAuth2AuthorizedClientManager serviceTokenManager(ClientRegistrationRepository registrations,
                                                      OAuth2AuthorizedClientService clients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    @Qualifier("face")
    RestClient faceRestClient(RestClient.Builder builder, PeopleProperties props, OAuth2AuthorizedClientManager serviceTokenManager) {
        OAuth2ClientHttpRequestInterceptor token = new OAuth2ClientHttpRequestInterceptor(serviceTokenManager);
        token.setClientRegistrationIdResolver(request -> REGISTRATION);
        token.setPrincipalResolver(request -> SERVICE);
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.face().timeout())
                .withReadTimeout(props.face().timeout());
        return builder.clone().baseUrl(props.face().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(token)
                .build();
    }
}
