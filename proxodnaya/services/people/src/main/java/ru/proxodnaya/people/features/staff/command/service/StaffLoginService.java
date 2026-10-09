package ru.proxodnaya.people.features.staff.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.StaffProperties;
import ru.proxodnaya.people.features.staff.command.repository.LoginCodeRepository;
import ru.proxodnaya.people.features.staff.command.repository.SessionRepository;
import ru.proxodnaya.people.features.staff.command.repository.StaffRepository;
import ru.proxodnaya.people.features.staff.gateway.StaffMailer;
import ru.proxodnaya.people.features.staff.model.AccessAction;
import ru.proxodnaya.people.features.staff.model.Actor;
import ru.proxodnaya.people.features.staff.model.CodeSent;
import ru.proxodnaya.people.features.staff.model.StaffSession;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.model.StaffUser;
import ru.proxodnaya.people.features.token.api.TokenIssuer;

/**
 * Вход персонала по коду из письма (PPL-13, ADR-041): почта → 6 цифр → сессия 12 ч. Access-токен короткий, поэтому
 * отключённый сотрудник теряет доступ к сервисам не позже чем через {@code accessTtl}, а к people — сразу.
 */
@Service
public class StaffLoginService {

    private static final String INVALID = "Неверный код";

    private final LoginCodeRepository codes;
    private final StaffRepository staff;
    private final SessionRepository sessions;
    private final LoginCodes crypto;
    private final StaffMailer mailer;
    private final AccessJournal journal;
    private final TokenIssuer issuer;
    private final StaffProperties props;
    private final Clock clock;

    public StaffLoginService(LoginCodeRepository codes, StaffRepository staff, SessionRepository sessions, LoginCodes crypto,
                             StaffMailer mailer, AccessJournal journal, TokenIssuer issuer, StaffProperties props, Clock clock) {
        this.codes = codes;
        this.staff = staff;
        this.sessions = sessions;
        this.crypto = crypto;
        this.mailer = mailer;
        this.journal = journal;
        this.issuer = issuer;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Отправляет код. Ответ одинаковый для любого адреса; письмо — только активным и приглашённым.
     *
     * @param rawEmail адрес
     * @return маска адреса и сроки
     * @throws DomainException LOGIN_RESEND_TOO_SOON раньше {@code resendAfter}; SERVICE_UNAVAILABLE — письмо не ушло
     */
    @Transactional
    public CodeSent requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        codes.lock(email).filter(row -> now.isBefore(row.resendAt())).ifPresent(row -> {
            long wait = Math.max(1, Duration.between(now, row.resendAt()).toSeconds());
            throw new DomainException(ErrorCode.LOGIN_RESEND_TOO_SOON, "Повторно отправить код можно через " + wait + " с");
        });
        boolean eligible = staff.lockByEmail(email).filter(user -> user.status() != StaffStatus.DISABLED).isPresent();
        String salt = crypto.randomToken(false);
        String code = crypto.code();
        Instant expires = now.plus(props.codeTtl());
        Instant resend = now.plus(props.resendAfter());
        codes.put(email, eligible ? crypto.hash(salt, code) : null, salt, expires, resend);
        if (eligible) {
            mailer.loginCode(email, code, props.codeTtl().toMinutes());
        }
        return new CodeSent(LoginCodes.mask(email), expires.toEpochMilli(), resend.toEpochMilli());
    }

    /**
     * Проверяет код и открывает сессию. Первый вход приглашённого делает его активным (JOIN).
     *
     * @param rawEmail адрес
     * @param code     код из письма
     * @return сессия
     * @throws DomainException LOGIN_CODE_INVALID, LOGIN_CODE_EXPIRED, LOGIN_CODE_LOCKED
     */
    @Transactional(noRollbackFor = DomainException.class)
    public StaffSession verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();
        LoginCodeRepository.CodeRow row = codes.lock(email)
                .orElseThrow(() -> new DomainException(ErrorCode.LOGIN_CODE_INVALID, INVALID + " — запросите новый"));
        if (row.attempts() >= props.maxAttempts()) {
            throw locked();
        }
        if (!now.isBefore(row.expiresAt())) {
            throw new DomainException(ErrorCode.LOGIN_CODE_EXPIRED, "Код устарел — запросите новый");
        }
        if (!LoginCodes.same(row.codeHash(), crypto.hash(row.salt(), code))) {
            int left = props.maxAttempts() - codes.failed(email);
            if (left <= 0) {
                throw locked();
            }
            throw new DomainException(ErrorCode.LOGIN_CODE_INVALID, INVALID + ". Осталось попыток: " + left);
        }
        codes.delete(email);
        StaffUser user = staff.lockByEmail(email).filter(found -> found.status() != StaffStatus.DISABLED)
                .orElseThrow(() -> new DomainException(ErrorCode.LOGIN_CODE_INVALID, INVALID + " — запросите новый"));
        if (user.status() == StaffStatus.INVITED) {
            staff.setStatus(user.id(), StaffStatus.ACTIVE);
            user = new StaffUser(user.id(), user.name(), user.email(), user.role(), StaffStatus.ACTIVE, now);
            journal.write(Actor.of(user), user, AccessAction.JOIN, null, now);
        }
        journal.write(Actor.of(user), user, AccessAction.LOGIN, null, now);
        staff.touch(user.id(), now);
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        String refresh = crypto.randomToken(true);
        Instant sessionEnd = now.plus(props.sessionTtl());
        sessions.insert(sessionId, user.id(), crypto.hashToken(refresh), now, sessionEnd);
        return session(user, sessionId, refresh, sessionEnd, now);
    }

    /**
     * Продлевает access-токен; refresh одноразовый (ротация). Роль берётся текущая — смена роли доходит без перелогина.
     *
     * @param refreshToken refresh из прошлого ответа
     * @return новая пара токенов
     * @throws DomainException SESSION_EXPIRED — сессия закрыта, истекла или сотрудник отключён
     */
    @Transactional
    public StaffSession refresh(String refreshToken) {
        Instant now = clock.instant();
        SessionRepository.SessionRow row = sessions.lockByRefresh(crypto.hashToken(refreshToken), now)
                .orElseThrow(StaffLoginService::expired);
        StaffUser user = staff.lock(row.staffId()).filter(found -> found.status() == StaffStatus.ACTIVE).orElse(null);
        if (user == null) {
            sessions.revoke(row.id(), now);
            throw expired();
        }
        String refresh = crypto.randomToken(true);
        sessions.rotate(row.id(), crypto.hashToken(refresh));
        staff.touch(user.id(), now);
        return session(user, row.id(), refresh, row.expiresAt(), now);
    }

    /**
     * Закрывает сессию.
     *
     * @param sessionId claim {@code sid}
     * @param staffId   сотрудник
     */
    @Transactional
    public void logout(UUID sessionId, UUID staffId) {
        Instant now = clock.instant();
        sessions.revoke(sessionId, now);
        staff.lock(staffId).ifPresent(user -> journal.write(Actor.of(user), user, AccessAction.LOGOUT, null, now));
    }

    private StaffSession session(StaffUser user, UUID sessionId, String refresh, Instant sessionEnd, Instant now) {
        Duration ttl = Duration.between(now, sessionEnd);
        if (ttl.compareTo(props.accessTtl()) > 0) {
            ttl = props.accessTtl();
        }
        TokenIssuer.Issued token = issuer.issue(user.id().toString(), List.of(user.role().name()), ttl,
                Map.of("name", user.name(), "email", user.email(), "preferred_username", user.email(), "sid", sessionId.toString()));
        return new StaffSession(token.token(), refresh, token.expiresAt(), sessionEnd.toEpochMilli(), user);
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private DomainException locked() {
        return new DomainException(ErrorCode.LOGIN_CODE_LOCKED, "Код введён неверно " + props.maxAttempts() + " раз — запросите новый");
    }

    private static DomainException expired() {
        return new DomainException(ErrorCode.SESSION_EXPIRED, "Сессия завершена — войдите снова");
    }
}
