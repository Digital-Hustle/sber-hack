package ru.proxodnaya.people.features.staff.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.staff.command.repository.SessionRepository;
import ru.proxodnaya.people.features.staff.command.repository.StaffRepository;
import ru.proxodnaya.people.features.staff.gateway.StaffMailer;
import ru.proxodnaya.people.features.staff.model.AccessAction;
import ru.proxodnaya.people.features.staff.model.Actor;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.model.StaffUser;

/**
 * Управление персоналом (ADR-041): приглашение, роль, отключение. Последнего активного администратора нельзя
 * разжаловать или отключить ({@code LAST_ADMIN}). Каждое действие — в журнал доступа.
 */
@Service
public class StaffCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(StaffCommandService.class);
    private static final String ADMIN_LABEL = "Администратор";
    private static final Map<StaffRole, String> LABELS = Map.of(StaffRole.ADMIN, ADMIN_LABEL,
            StaffRole.SECURITY_OFFICER, "Служба безопасности", StaffRole.MANAGER, "Руководитель участка",
            StaffRole.INSTALLER, "Инженер терминалов", StaffRole.GUARD, "Охранник");

    private final StaffRepository staff;
    private final SessionRepository sessions;
    private final AccessJournal journal;
    private final StaffMailer mailer;
    private final Clock clock;

    public StaffCommandService(StaffRepository staff, SessionRepository sessions, AccessJournal journal, StaffMailer mailer,
                               Clock clock) {
        this.staff = staff;
        this.sessions = sessions;
        this.journal = journal;
        this.mailer = mailer;
        this.clock = clock;
    }

    /**
     * Приглашает сотрудника: статус INVITED, письмо со ссылкой на вход (если почта не ушла — приглашение всё равно создано).
     *
     * @param actor кто приглашает
     * @param name  имя
     * @param email почта
     * @param role  роль
     * @return сотрудник
     * @throws DomainException CONFLICT — почта уже занята
     */
    @Transactional
    public StaffUser invite(Actor actor, String name, String email, StaffRole role) {
        Instant now = clock.instant();
        StaffUser user = new StaffUser(UuidCreator.getTimeOrderedEpoch(), name.strip(), email.strip().toLowerCase(Locale.ROOT), role,
                StaffStatus.INVITED, null);
        if (!staff.insert(user, now)) {
            throw new DomainException(ErrorCode.CONFLICT, "Пользователь с такой почтой уже есть");
        }
        journal.write(actor, user, AccessAction.INVITE, null, now);
        try {
            mailer.invitation(user.email(), user.name(), LABELS.get(role), actor.name() == null ? ADMIN_LABEL : actor.name());
        } catch (DomainException error) {
            LOG.warn("Приглашение {} создано, но письмо не ушло", user.id());
        }
        return user;
    }

    /**
     * Меняет роль; новая роль попадёт в токен при ближайшем продлении.
     *
     * @param actor кто
     * @param id    сотрудник
     * @param role  новая роль
     * @return сотрудник
     * @throws DomainException NOT_FOUND, LAST_ADMIN
     */
    @Transactional
    public StaffUser setRole(Actor actor, UUID id, StaffRole role) {
        staff.lockAdmins();
        StaffUser user = get(id);
        if (user.role() == role) {
            return user;
        }
        if (user.role() == StaffRole.ADMIN) {
            guardLastAdmin(user);
        }
        staff.setRole(id, role);
        StaffUser changed = new StaffUser(user.id(), user.name(), user.email(), role, user.status(), user.lastSeen());
        journal.write(actor, changed, AccessAction.ROLE, user.role(), clock.instant());
        return changed;
    }

    /**
     * Отключает или включает сотрудника. Отключение закрывает все его сессии.
     *
     * @param actor  кто
     * @param id     сотрудник
     * @param active включить
     * @return сотрудник
     * @throws DomainException NOT_FOUND, LAST_ADMIN
     */
    @Transactional
    public StaffUser setActive(Actor actor, UUID id, boolean active) {
        staff.lockAdmins();
        StaffUser user = get(id);
        Instant now = clock.instant();
        StaffStatus status;
        if (active) {
            if (user.status() != StaffStatus.DISABLED) {
                return user;
            }
            status = user.lastSeen() == null ? StaffStatus.INVITED : StaffStatus.ACTIVE;
        } else {
            if (user.status() == StaffStatus.DISABLED) {
                return user;
            }
            if (user.role() == StaffRole.ADMIN) {
                guardLastAdmin(user);
            }
            status = StaffStatus.DISABLED;
            sessions.revokeAll(id, now);
        }
        staff.setStatus(id, status);
        StaffUser changed = new StaffUser(user.id(), user.name(), user.email(), user.role(), status, user.lastSeen());
        journal.write(actor, changed, active ? AccessAction.ENABLE : AccessAction.DISABLE, null, now);
        return changed;
    }

    /**
     * Первый администратор из env, если персонала ещё нет (иначе в кабинет не войти).
     *
     * @param email почта или пусто
     */
    @Transactional
    public void bootstrap(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        staff.lockAdmins();
        if (!staff.any()) {
            createAdmin(email);
        }
    }

    private void createAdmin(String email) {
        StaffUser admin = new StaffUser(UuidCreator.getTimeOrderedEpoch(), ADMIN_LABEL, email.strip().toLowerCase(Locale.ROOT),
                StaffRole.ADMIN, StaffStatus.INVITED, null);
        Instant now = clock.instant();
        staff.insert(admin, now);
        journal.write(new Actor(null, "Система"), admin, AccessAction.INVITE, null, now);
        LOG.info("Создан первый администратор {} — вход по коду из письма", admin.id());
    }

    private StaffUser get(UUID id) {
        return staff.lock(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Сотрудник не найден"));
    }

    private void guardLastAdmin(StaffUser user) {
        if (user.status() == StaffStatus.ACTIVE && staff.activeAdmins() <= 1) {
            throw new DomainException(ErrorCode.LAST_ADMIN, "Это последний администратор — сначала назначьте другого");
        }
    }
}
