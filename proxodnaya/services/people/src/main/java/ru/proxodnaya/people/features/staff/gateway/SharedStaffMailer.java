package ru.proxodnaya.people.features.staff.gateway;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.SharedMailProperties;

/**
 * Письма через единый SMTP-клиент всех кейсов. Код не включается в логи и тему.
 */
@Component
@ConditionalOnProperty(name = "people.shared-mail.enabled", havingValue = "true")
public class SharedStaffMailer implements StaffMailer {

    private static final String SERVICE = "proxodnaya";
    private static final int INVITATION_MINUTES = 10;
    private final RestClient client;

    public SharedStaffMailer(SharedMailProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.timeout());
        client = RestClient.builder().baseUrl(props.url()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.token()).build();
    }

    @Override
    public void loginCode(String email, String code, long minutes) {
        send(new MailRq(SERVICE, "login", email, code, minutes, "", "", ""));
    }

    @Override
    public void invitation(String email, String name, String role, String invitedBy) {
        send(new MailRq(SERVICE, "invitation", email, "", INVITATION_MINUTES, name, role, invitedBy));
    }

    private void send(MailRq request) {
        try {
            client.post().uri("/internal/mail").body(request).retrieve().toBodilessEntity();
        } catch (RestClientResponseException error) {
            throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Отправка почты Digital Hustle недоступна: SMTP ещё не подключён или не принял письмо");
        } catch (RestClientException error) {
            throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "Не удалось связаться с почтовым сервисом — попробуйте позже");
        }
    }

    private record MailRq(String service, String kind, String email, String code, long minutes, String name, String role,
                          @JsonProperty("invited_by") String invitedBy) {
    }
}
