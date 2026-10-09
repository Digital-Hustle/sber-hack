package ru.proxodnaya.shift.features.shift.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.exception.ConflictException;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.shift.command.repository.ShiftRepository;
import ru.proxodnaya.shift.features.shift.command.repository.ShiftTemplateRepository;
import ru.proxodnaya.shift.features.shift.model.BulkResult;
import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.features.shift.model.ShiftDraft;
import ru.proxodnaya.shift.features.shift.model.ShiftTemplate;
import ru.proxodnaya.shift.features.shift.model.WeekdayWindow;

/**
 * Назначение смен и шаблонов (FR-20). Смены одного рабочего не пересекаются: проверка под advisory-блокировкой рабочего.
 */
@Service
public class ShiftCommandService {

    static final Duration MAX_SHIFT = Duration.ofHours(24);
    static final int MAX_BULK_DAYS = 62;
    private static final String SAME_START_END = "Начало и конец смены совпадают";

    private final ShiftRepository shifts;
    private final ShiftTemplateRepository templates;
    private final ShiftProperties props;
    private final Clock clock;

    public ShiftCommandService(ShiftRepository shifts, ShiftTemplateRepository templates, ShiftProperties props, Clock clock) {
        this.shifts = shifts;
        this.templates = templates;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Назначает одну смену.
     *
     * @param draft   смена
     * @param actorId кто назначает
     * @return созданная смена
     * @throws DomainException   VALIDATION_ERROR — конец не после начала или смена дольше 24 ч
     * @throws NotFoundException шаблон не найден
     * @throws ConflictException смена пересекается с уже назначенной
     */
    @Transactional
    public Shift create(ShiftDraft draft, String actorId) {
        checkDuration(draft);
        if (draft.templateId() != null && !templates.exists(draft.templateId())) {
            throw new NotFoundException("Шаблон смены не найден");
        }
        shifts.lockWorker(draft.workerId());
        if (shifts.overlaps(draft.workerId(), draft.start(), draft.end())) {
            throw new ConflictException("Смена пересекается с уже назначенной этому сотруднику");
        }
        Shift shift = new Shift(UuidCreator.getTimeOrderedEpoch(), draft.workerId(), draft.start(), draft.end(), draft.templateId());
        shifts.insert(shift, actorId, clock.instant());
        return shift;
    }

    /**
     * Назначает бригаде смены по недельному графику; пересекающиеся с уже назначенными пропускает.
     *
     * @param workerIds рабочие
     * @param first     первая дата
     * @param last      последняя дата, включительно
     * @param windows   время по дням недели
     * @param actorId   кто назначает
     * @return сколько создано и пропущено
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 62 дней, день недели повторяется
     */
    @Transactional
    public BulkResult bulk(List<String> workerIds, LocalDate first, LocalDate last, List<WeekdayWindow> windows, String actorId) {
        checkBulk(first, last, windows);
        List<ShiftPlanner.Slot> slots = ShiftPlanner.plan(props.siteZone(), first, last, windows);
        Set<String> workers = new LinkedHashSet<>(workerIds);
        int created = 0;
        int skipped = 0;
        for (String workerId : workers.stream().sorted().toList()) {
            shifts.lockWorker(workerId);
            for (ShiftPlanner.Slot slot : slots) {
                if (shifts.overlaps(workerId, slot.start(), slot.end())) {
                    skipped++;
                } else {
                    shifts.insert(new Shift(UuidCreator.getTimeOrderedEpoch(), workerId, slot.start(), slot.end(), null), actorId,
                            clock.instant());
                    created++;
                }
            }
        }
        return new BulkResult(created, skipped);
    }

    /**
     * Создаёт шаблон смены.
     *
     * @param name  название
     * @param start начало
     * @param end   конец
     * @return шаблон
     * @throws DomainException   VALIDATION_ERROR — начало и конец совпадают
     * @throws ConflictException название занято
     */
    @Transactional
    public ShiftTemplate createTemplate(String name, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, SAME_START_END);
        }
        ShiftTemplate template = new ShiftTemplate(UuidCreator.getTimeOrderedEpoch(), name.strip(), start, end);
        if (!templates.insert(template, clock.instant())) {
            throw new ConflictException("Шаблон с таким названием уже есть");
        }
        return template;
    }

    /**
     * Удаляет смену. Интервалы присутствия остаются, связь со сменой обнуляется.
     *
     * @param id смена
     * @throws NotFoundException смены нет
     */
    @Transactional
    public void delete(UUID id) {
        if (!shifts.delete(id)) {
            throw new NotFoundException("Смена не найдена");
        }
    }

    private static void checkDuration(ShiftDraft draft) {
        if (!draft.end().isAfter(draft.start())) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Конец смены должен быть позже начала");
        }
        if (Duration.between(draft.start(), draft.end()).compareTo(MAX_SHIFT) > 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Смена не может быть дольше 24 часов");
        }
    }

    private static void checkBulk(LocalDate first, LocalDate last, List<WeekdayWindow> windows) {
        if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) >= MAX_BULK_DAYS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Период — от 1 до " + MAX_BULK_DAYS + " дней");
        }
        Set<Integer> days = windows.stream().map(WeekdayWindow::weekday).collect(Collectors.toSet());
        if (days.size() != windows.size()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "День недели указан дважды");
        }
        if (windows.stream().anyMatch(window -> window.start().equals(window.end()))) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, SAME_START_END);
        }
    }
}
