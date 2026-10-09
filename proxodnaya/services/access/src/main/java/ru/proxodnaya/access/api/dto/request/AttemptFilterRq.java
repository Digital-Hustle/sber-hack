package ru.proxodnaya.access.api.dto.request;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Фильтры журнала проходов. Все поля опциональны.
 *
 * @param workerId   фильтр по рабочему
 * @param checkpoint фильтр по проходной
 * @param decision   фильтр по решению (ALLOW, DENY, MANUAL, ERROR)
 * @param code       фильтр по коду причины
 * @param source     фильтр по источнику (KIOSK, MANUAL, OFFLINE, SIMULATOR)
 * @param from       от (включительно)
 * @param to         до (включительно)
 * @param cursor     курсор последней записи предыдущей страницы (ISO instant)
 * @param limit      размер страницы (по умолчанию 50)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptFilterRq {

    private String workerId;
    private String checkpoint;
    private String decision;
    private String code;
    private String source;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    private String cursor;
    @Builder.Default
    private int limit = 50;
}
