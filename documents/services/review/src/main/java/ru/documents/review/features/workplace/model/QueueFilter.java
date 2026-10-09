package ru.documents.review.features.workplace.model;

import java.util.List;

/**
 * Фильтр очереди.
 *
 * @param statuses  статусы (пусто — всё, кроме черновиков)
 * @param q         поиск по ФИО или началу номера заявки
 * @param assignee  me | none | логин
 * @param attention только «есть что перепроверить»
 * @param sort      waiting | updated
 * @param offset    смещение
 * @param limit     размер страницы
 */
public record QueueFilter(List<String> statuses, String q, String assignee, boolean attention, String sort,
                          int offset, int limit) {
}
