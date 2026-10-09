package ru.proxodnaya.people.features.device.command.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.TokenProperties;
import ru.proxodnaya.people.features.device.command.repository.ChallengeRepository;
import ru.proxodnaya.people.features.device.command.repository.DeviceRepository;
import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.features.device.model.DeviceChallenge;
import ru.proxodnaya.people.features.token.api.TokenIssuer;
import ru.proxodnaya.people.features.worker.api.WorkerDirectory;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Device-токен телефона (PPL-05, ADR-040): телефон доказывает владение ключом, привязанным при активации, подписью
 * одноразового challenge — и получает короткий JWT people с ролью WORKER для своих экранов.
 */
@Service
public class DeviceAuthService {

    private static final int CHALLENGE_BYTES = 32;
    /** Подпись WebCrypto ECDSA — r||s по 32 байта (IEEE P1363); DER тоже принимаем. */
    private static final int P1363_LENGTH = 64;
    private static final String WORKER = "WORKER";

    private final ChallengeRepository challenges;
    private final DeviceRepository devices;
    private final WorkerDirectory workers;
    private final TokenIssuer issuer;
    private final TokenProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DeviceAuthService(ChallengeRepository challenges, DeviceRepository devices, WorkerDirectory workers,
                             TokenIssuer issuer, TokenProperties props, Clock clock) {
        this.challenges = challenges;
        this.devices = devices;
        this.workers = workers;
        this.issuer = issuer;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Выдаёт challenge. Наличие устройства здесь не проверяется — чтобы по ответу нельзя было перебирать id.
     *
     * @param deviceId устройство
     * @return challenge и срок
     */
    @Transactional
    public DeviceChallenge challenge(String deviceId) {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expires = now.plus(props.challengeTtl());
        challenges.insert(value, deviceId, now, expires);
        return new DeviceChallenge(value, expires.toEpochMilli());
    }

    /**
     * Обменивает подписанный challenge на device-токен.
     *
     * @param deviceId  устройство
     * @param challenge выданный challenge
     * @param signature подпись ECDSA P-256/SHA-256 над {@code auth|<deviceId>|<challenge>}, base64url
     * @return токен
     * @throws DomainException CHALLENGE_INVALID, DEVICE_UNKNOWN, DEVICE_REVOKED, DEVICE_MISMATCH, WORKER_BLOCKED
     */
    @Transactional(noRollbackFor = DomainException.class)
    public TokenIssuer.Issued token(String deviceId, String challenge, String signature) {
        Instant now = clock.instant();
        if (!challenges.consume(challenge, deviceId, now)) {
            throw new DomainException(ErrorCode.CHALLENGE_INVALID, "Запрос входа устарел — повторите");
        }
        Device device = devices.find(deviceId)
                .orElseThrow(() -> new DomainException(ErrorCode.DEVICE_UNKNOWN, "Телефон не привязан — активируйте его по приглашению"));
        if (!Device.ACTIVE.equals(device.status())) {
            throw new DomainException(ErrorCode.DEVICE_REVOKED, "Телефон отвязан — попросите новое приглашение");
        }
        if (!verify(device.publicKeyJwk(), "auth|" + deviceId + "|" + challenge, signature)) {
            throw new DomainException(ErrorCode.DEVICE_MISMATCH, "Подпись телефона не прошла проверку");
        }
        workers.lock(device.workerId())
                .filter(worker -> worker.status() == WorkerStatus.ACTIVE)
                .orElseThrow(() -> new DomainException(ErrorCode.WORKER_BLOCKED, "Доступ заблокирован"));
        return issuer.issue(device.workerId().toString(), List.of(WORKER), props.deviceTtl(), Map.of("did", deviceId));
    }

    static boolean verify(String jwk, String payload, String signature) {
        try {
            ECPublicKey key = ECKey.parse(jwk).toECPublicKey();
            byte[] raw = Base64.getUrlDecoder().decode(signature.strip().replace('+', '-').replace('/', '_').replace("=", ""));
            Signature verifier = Signature.getInstance(raw.length == P1363_LENGTH ? "SHA256withECDSAinP1363Format" : "SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(raw);
        } catch (GeneralSecurityException | ParseException | JOSEException | IllegalArgumentException error) {
            return false;
        }
    }
}
