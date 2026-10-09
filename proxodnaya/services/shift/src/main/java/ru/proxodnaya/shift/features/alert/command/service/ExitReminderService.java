package ru.proxodnaya.shift.features.alert.command.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.config.properties.ReminderProperties;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.alert.api.ExitReminderDue;
import ru.proxodnaya.shift.features.alert.command.repository.ReminderRepository;
import ru.proxodnaya.shift.features.alert.model.OverdueShift;

/**
 * B-04: через {@code exitAfter} после конца смены человеку, не отметившему выход, — одно напоминание (SSE {@code reminder.exit}).
 * Окно — до автозакрытия интервала: позже выход уже закрыт как {@code AUTO_SHIFT_END}.
 */
@Service
public class ExitReminderService {

    private final ReminderRepository repository;
    private final ApplicationEventPublisher events;
    private final ShiftProperties shiftProps;
    private final ReminderProperties props;
    private final Clock clock;

    public ExitReminderService(ReminderRepository repository, ApplicationEventPublisher events, ShiftProperties shiftProps,
                               ReminderProperties props, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.shiftProps = shiftProps;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Шаг напоминаний.
     *
     * @return сколько напоминаний отправлено
     */
    @Transactional
    public int remindOverdue() {
        Instant now = clock.instant();
        int sent = 0;
        for (OverdueShift shift : repository.overdue(now.minus(shiftProps.autoCloseAfter()), now.minus(props.exitAfter()))) {
            boolean inShift = !shift.enteredAt().isBefore(shift.startsAt().minus(shiftProps.earlyEntry()));
            if (inShift && repository.markSent(shift.shiftId(), shift.workerId(), now)) {
                events.publishEvent(new ExitReminderDue(shift.shiftId(), shift.workerId(), shift.endsAt(), now));
                sent++;
            }
        }
        return sent;
    }
}
