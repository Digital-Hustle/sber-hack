package ru.proxodnaya.people.features.staff.command.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.people.features.staff.command.repository.AccessLogRepository;
import ru.proxodnaya.people.features.staff.model.AccessAction;
import ru.proxodnaya.people.features.staff.model.Actor;
import ru.proxodnaya.people.features.staff.model.AccessEntry;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffUser;

/**
 * Запись в журнал доступа — в той же транзакции, что и действие.
 */
@Component
public class AccessJournal {

    private final AccessLogRepository log;

    public AccessJournal(AccessLogRepository log) {
        this.log = log;
    }

    /**
     * Действие над сотрудником.
     *
     * @param actor  кто
     * @param target над кем
     * @param action действие
     * @param from   роль до или {@code null}
     * @param moment момент
     */
    public void write(Actor actor, StaffUser target, AccessAction action, StaffRole from, Instant moment) {
        String toRole = action == AccessAction.INVITE || action == AccessAction.ROLE ? target.role().name() : null;
        log.insert(new AccessEntry(UuidCreator.getTimeOrderedEpoch(), moment, actor.id(), actor.name(), target.id(), target.name(), action,
                from == null ? null : from.name(), toRole, null));
    }
}
