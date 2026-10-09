package ru.documents.gateway.limit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фиксированное окно на ключ «правило + IP» в памяти процесса (gateway — один экземпляр, ADR-018).
 * Старые окна вычищаются при обращении, чтобы память не росла от сканеров.
 */
public class RateLimiter {

    private static final int SWEEP_EVERY = 1024;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private int calls;

    /**
     * Ограничитель.
     *
     * @param clock часы (в тестах — фиксированные)
     */
    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Учесть запрос.
     *
     * @param key    ключ корзины
     * @param limit  сколько запросов разрешено за окно
     * @param window длина окна
     * @return 0, если запрос разрешён, иначе сколько секунд ждать до нового окна
     */
    public long acquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        sweep(now);
        Window w = windows.compute(key, (k, old) -> old == null || !now.isBefore(old.end())
                ? new Window(now.plus(window), 1)
                : new Window(old.end(), old.count() + 1));
        if (w.count() <= limit) {
            return 0;
        }
        return Math.max(1, Duration.between(now, w.end()).toSeconds() + 1);
    }

    private synchronized void sweep(Instant now) {
        if (++calls % SWEEP_EVERY != 0) {
            return;
        }
        for (Iterator<Window> it = windows.values().iterator(); it.hasNext();) {
            if (!now.isBefore(it.next().end())) {
                it.remove();
            }
        }
    }

    private record Window(Instant end, int count) {
    }
}
