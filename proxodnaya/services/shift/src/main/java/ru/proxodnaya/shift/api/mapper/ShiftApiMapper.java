package ru.proxodnaya.shift.api.mapper;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import ru.proxodnaya.shift.api.dto.request.ShiftRq;
import ru.proxodnaya.shift.api.dto.request.WeekdayRq;
import ru.proxodnaya.shift.api.dto.response.ActiveShiftRs;
import ru.proxodnaya.shift.api.dto.response.BulkShiftRs;
import ru.proxodnaya.shift.api.dto.response.HoursRs;
import ru.proxodnaya.shift.api.dto.response.IntervalPageRs;
import ru.proxodnaya.shift.api.dto.response.IntervalRs;
import ru.proxodnaya.shift.api.dto.response.PageRs;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.ShiftRs;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplateRs;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplatesRs;
import ru.proxodnaya.shift.api.dto.response.SummaryRs;
import ru.proxodnaya.shift.api.dto.response.WorkerMeRs;
import ru.proxodnaya.shift.features.analytics.query.model.HoursReport;
import ru.proxodnaya.shift.features.analytics.query.model.Summary;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.shift.api.ActiveShift;
import ru.proxodnaya.shift.features.shift.model.BulkResult;
import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.features.shift.model.ShiftDraft;
import ru.proxodnaya.shift.features.shift.model.ShiftTemplate;
import ru.proxodnaya.shift.features.shift.model.WeekdayWindow;
import ru.proxodnaya.shift.features.worker.model.WorkerStatus;
import ru.proxodnaya.shift.shared.Page;

/**
 * Модели shift ↔ DTO. Вручную: вложенные записи и вычисляемое {@code incomplete} MapStruct не упрощает.
 */
@Component
public class ShiftApiMapper {
    /**
     * Запрос → смена к созданию.
     *
     * @param request запрос
     * @return черновик
     */
    public ShiftDraft convert(ShiftRq request) {
        return new ShiftDraft(request.workerId().strip(), request.start(), request.end(), request.templateId());
    }

    /**
     * Дни недели запроса → модель.
     *
     * @param days дни
     * @return окна
     */
    public List<WeekdayWindow> convert(List<WeekdayRq> days) {
        return days.stream().map(day -> new WeekdayWindow(day.weekday(), day.start(), day.end())).toList();
    }

    /**
     * Смена.
     *
     * @param shift смена
     * @return DTO
     */
    public ShiftRs convert(Shift shift) {
        return new ShiftRs(shift.id(), shift.workerId(), shift.start(), shift.end(), shift.templateId());
    }

    /**
     * Итог массового назначения.
     *
     * @param result итог
     * @return DTO
     */
    public BulkShiftRs convert(BulkResult result) {
        return new BulkShiftRs(result.created(), result.skipped());
    }

    /**
     * Шаблон.
     *
     * @param template шаблон
     * @return DTO
     */
    public ShiftTemplateRs convert(ShiftTemplate template) {
        return new ShiftTemplateRs(template.id(), template.name(), template.start(), template.end());
    }

    /**
     * Смена для прохода.
     *
     * @param shift смена
     * @return DTO
     */
    public ActiveShiftRs convert(ActiveShift shift) {
        return new ActiveShiftRs(shift.shiftId(), shift.start(), shift.end(), shift.windowOk());
    }

    /**
     * Сводка.
     *
     * @param summary сводка
     * @return DTO
     */
    public SummaryRs convert(Summary summary) {
        return new SummaryRs(summary.at(), summary.inside(),
                summary.byZone().stream().map(zone -> new SummaryRs.ZoneInsideRs(zone.zoneId(), zone.inside())).toList(),
                summary.incomplete(),
                new SummaryRs.PassesRs(summary.passes().allowed(), summary.passes().manual(), summary.passes().denied()),
                summary.method());
    }

    /**
     * Часы.
     *
     * @param report отчёт
     * @return DTO
     */
    public HoursRs convert(HoursReport report) {
        return new HoursRs(report.from(), report.to(), report.items().stream()
                .map(row -> new HoursRs.WorkerHoursRs(row.workerId(), row.workedMinutes(), row.intervals(), row.incomplete()))
                .toList(), report.method());
    }

    /**
     * Статус рабочего.
     *
     * @param status статус
     * @return DTO
     */
    public WorkerMeRs convert(WorkerStatus status) {
        return new WorkerMeRs(status.inside(), status.zoneId(), status.since(), status.shift() == null ? null : convert(status.shift()),
                status.workedToday().toMillis());
    }

    /**
     * Страница смен.
     *
     * @param page страница
     * @return DTO
     */
    public ShiftPageRs convertShifts(Page<Shift> page) {
        return new ShiftPageRs(page.items().stream().map(this::convert).toList(), new PageRs(page.next(), page.total()));
    }

    /**
     * Шаблоны.
     *
     * @param templates шаблоны
     * @return DTO
     */
    public ShiftTemplatesRs convertTemplates(List<ShiftTemplate> templates) {
        return new ShiftTemplatesRs(templates.stream().map(this::convert).toList());
    }

    /**
     * Страница интервалов.
     *
     * @param page       страница
     * @param incomplete неполный ли интервал
     * @return DTO
     */
    public IntervalPageRs convertIntervals(Page<Interval> page, Predicate<Interval> incomplete) {
        return new IntervalPageRs(page.items().stream().map(row -> interval(row, incomplete.test(row))).toList(),
                new PageRs(page.next(), page.total()));
    }

    private static IntervalRs interval(Interval row, boolean incomplete) {
        return new IntervalRs(row.id(), row.workerId(), row.zoneId(), row.shiftId(), row.enteredAt(), row.exitedAt(),
                row.closeReason() == null ? null : row.closeReason().name(), row.flags().stream().map(Enum::name).toList(), incomplete);
    }
}
