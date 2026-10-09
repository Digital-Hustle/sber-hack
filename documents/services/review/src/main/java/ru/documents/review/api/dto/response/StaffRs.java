package ru.documents.review.api.dto.response;

import java.util.List;
import ru.documents.review.features.staff.model.StaffMember;

/**
 * Сотрудники рабочего места.
 *
 * @param items сотрудники
 */
public record StaffRs(List<StaffMember> items) {
}
