package ru.proxodnaya.access.api.dto.response;

import java.util.List;

/**
 * Терминалы для кабинета.
 *
 * @param items сопряжённые и ждущие сопряжения
 */
public record KioskListRs(List<KioskRs> items) {
}
