package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Профиль доступа для access: всё, что нужно решению о проходе.
 *
 * @param status     ACTIVE | BLOCKED
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param deviceIds  действующие телефоны
 * @param permits    допуски со сроком
 * @param zoneIds    зоны допуска
 */
public record AccessProfileRs(String status, String fullName, String position, String contractor,
                              List<String> deviceIds, List<PermitRs> permits, List<String> zoneIds) {
}
