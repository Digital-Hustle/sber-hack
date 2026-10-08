package ru.proxodnaya.access.shared;

/**
 * Решение о проходе: ALLOW — пропуск, DENY — отказ, MANUAL — ручной проход охранника, ERROR — системная ошибка.
 */
public enum Decision {
    ALLOW,
    DENY,
    MANUAL,
    ERROR
}
