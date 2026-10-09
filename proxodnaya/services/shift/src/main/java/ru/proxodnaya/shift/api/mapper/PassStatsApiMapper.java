package ru.proxodnaya.shift.api.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.proxodnaya.shift.api.dto.response.LoadByHourRs;
import ru.proxodnaya.shift.api.dto.response.RefusalsRs;
import ru.proxodnaya.shift.features.analytics.query.model.HourlyLoad;
import ru.proxodnaya.shift.features.analytics.query.model.RefusalStats;

/**
 * Статистика проходов → DTO; доли считаются здесь, чтобы модель оставалась в целых числах.
 */
@Component
public class PassStatsApiMapper {

    /**
     * Отказы.
     *
     * @param stats статистика
     * @return DTO
     */
    public RefusalsRs convert(RefusalStats stats) {
        List<RefusalsRs.ReasonRs> reasons = stats.byReason().stream()
                .map(reason -> new RefusalsRs.ReasonRs(reason.reasonCode(), reason.count(), share(reason.count(), stats.denied()),
                        reason.system() ? "SYSTEM" : "LEGITIMATE"))
                .toList();
        return new RefusalsRs(stats.from(), stats.to(), stats.attempts(), stats.denied(), share(stats.denied(), stats.attempts()),
                stats.system(), stats.legitimate(), reasons, stats.method());
    }

    /**
     * Нагрузка.
     *
     * @param load нагрузка
     * @return DTO
     */
    public LoadByHourRs convert(HourlyLoad load) {
        List<LoadByHourRs.CellRs> cells = load.cells().stream()
                .map(cell -> new LoadByHourRs.CellRs(cell.weekday(), cell.hour(), cell.attempts(), cell.allowed(), cell.denied()))
                .toList();
        LoadByHourRs.CellRs peak = cells.stream().max(Comparator.comparingLong(LoadByHourRs.CellRs::attempts)).orElse(null);
        return new LoadByHourRs(load.from(), load.to(), load.zone().getId(), cells, peak, load.method());
    }

    private static double share(long part, long whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
