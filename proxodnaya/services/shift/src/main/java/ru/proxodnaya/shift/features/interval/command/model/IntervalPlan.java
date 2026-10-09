package ru.proxodnaya.shift.features.interval.command.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ru.proxodnaya.shift.features.interval.model.CloseReason;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFlag;
import ru.proxodnaya.shift.features.interval.model.PassEvent;

/**
 * Что сделать с интервалами по проходу (FR-22/23): закрыть открытый и/или открыть новый.
 *
 * @param closeReason причина закрытия открытого интервала или {@code null} — не закрывать
 * @param closeFlags  пометки закрываемого
 * @param openNew     открыть новый интервал
 */
public record IntervalPlan(CloseReason closeReason, List<IntervalFlag> closeFlags, boolean openNew) {

    private static final IntervalPlan NOTHING = new IntervalPlan(null, List.of(), false);

    /**
     * Правила: вход без открытого — открыть; вход в другую зону — {@code ZONE_CHANGE} + открыть; повторный вход в ту же
     * зону — {@code REENTRY} с {@code UNCONFIRMED_EXIT} + открыть; выход — {@code EXIT}; выход без открытого и события
     * старше открытого интервала (пришли не по порядку) — ничего.
     *
     * @param open  открытый интервал рабочего
     * @param event проход, который состоялся
     * @return план
     */
    public static IntervalPlan of(Optional<Interval> open, PassEvent event) {
        IntervalPlan plan;
        if (open.isPresent() && event.ts().isBefore(open.get().enteredAt())) {
            plan = NOTHING;
        } else if (!event.entry()) {
            plan = open.isPresent() ? new IntervalPlan(CloseReason.EXIT, List.of(), false) : NOTHING;
        } else if (open.isEmpty()) {
            plan = new IntervalPlan(null, List.of(), true);
        } else if (Objects.equals(open.get().zoneId(), event.zoneId())) {
            plan = new IntervalPlan(CloseReason.REENTRY, List.of(IntervalFlag.UNCONFIRMED_EXIT), true);
        } else {
            plan = new IntervalPlan(CloseReason.ZONE_CHANGE, List.of(), true);
        }
        return plan;
    }

    /**
     * Нужно ли закрыть открытый интервал.
     *
     * @return {@code true}, если есть причина закрытия
     */
    public boolean closes() {
        return closeReason != null;
    }
}
