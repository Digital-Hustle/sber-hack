package ru.proxodnaya.people.shared;

/**
 * Статус рабочего. {@code DELETED} — мягкое удаление: в списках и API не виден.
 */
public enum WorkerStatus {
    ACTIVE,
    BLOCKED,
    DELETED
}
