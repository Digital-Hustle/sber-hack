package ru.proxodnaya.access.features.kiosk.command.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.config.properties.KioskProperties;
import ru.proxodnaya.access.features.kiosk.api.KioskPrincipal;
import ru.proxodnaya.access.features.kiosk.api.KioskSignature;
import ru.proxodnaya.access.features.kiosk.command.repository.KioskRepository;
import ru.proxodnaya.access.features.kiosk.model.Kiosk;
import ru.proxodnaya.access.features.qr.api.SignatureCheck;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Проверка подписи запроса терминала (ACC-12, FR-64): ключ терминала, окно времени и одноразовый nonce.
 * Подписанная строка: {@code kiosk|<kioskId>|<METHOD>|<path?query>|<ts>|<nonce>|<sha256b64u(body)>}.
 */
@Component
public class KioskAuthenticator {

    private static final int MAX_NONCE = 64;
    private static final String SEP = "|";

    private final KioskRepository repository;
    private final KioskService kiosks;
    private final SignatureCheck signatures;
    private final KioskProperties props;
    private final Clock clock;

    public KioskAuthenticator(KioskRepository repository, KioskService kiosks, SignatureCheck signatures, KioskProperties props,
                              Clock clock) {
        this.repository = repository;
        this.kiosks = kiosks;
        this.signatures = signatures;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Проверяет запрос.
     *
     * @param signature заголовки подписи
     * @param method    HTTP-метод
     * @param path      путь с запросом так, как его отправил терминал (с префиксом gateway)
     * @param body      тело
     * @return терминал
     * @throws DomainException {@code KIOSK_UNKNOWN} (ключ не зарегистрирован) или {@code KIOSK_SIGNATURE_INVALID}
     */
    public KioskPrincipal authenticate(KioskSignature signature, String method, String path, byte[] body) {
        Kiosk kiosk = parse(signature.kioskId()).flatMap(repository::find)
                .orElseThrow(() -> new DomainException(ErrorCode.KIOSK_UNKNOWN, "Терминал не зарегистрирован"));
        long sentAt = parseTs(signature.ts());
        if (Math.abs(clock.millis() - sentAt) > props.clockSkew().toMillis()) {
            throw invalid("Часы терминала расходятся с сервером — проверьте время на устройстве");
        }
        String nonce = signature.nonce();
        if (nonce == null || nonce.isBlank() || nonce.length() > MAX_NONCE || signature.signature() == null) {
            throw invalid("Запрос терминала без подписи");
        }
        String message = String.join(SEP, "kiosk", signature.kioskId(), method, path, signature.ts(), nonce, sha256(body));
        if (!signatures.verify(kiosk.publicKey(), message, signature.signature())) {
            throw invalid("Подпись терминала не сошлась");
        }
        if (!kiosks.useNonce(kiosk.id(), nonce)) {
            throw invalid("Повтор запроса терминала");
        }
        kiosks.touch(kiosk.id());
        return new KioskPrincipal(kiosk.id());
    }

    private static Optional<UUID> parse(String kioskId) {
        try {
            return kioskId == null ? Optional.empty() : Optional.of(UUID.fromString(kioskId));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private static long parseTs(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid("Неверное время запроса терминала");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.KIOSK_SIGNATURE_INVALID, message);
    }

    private static String sha256(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * Путь запроса так, как его подписывает терминал: gateway срезает префикс сервиса, терминал подписывает полный путь.
     *
     * @param prefix префикс gateway
     * @param uri    путь запроса
     * @param query  строка запроса или {@code null}
     * @return путь так, как его подписывает терминал
     */
    public static String signedPath(String prefix, String uri, String query) {
        return (prefix == null ? "" : prefix) + uri + (query == null || query.isEmpty() ? "" : "?" + query);
    }
}
