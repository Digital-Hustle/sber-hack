package ru.documents.review.api.controller.impl;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.documents.review.api.controller.StaffController;
import ru.documents.review.api.dto.request.EnabledRq;
import ru.documents.review.api.dto.request.StaffRq;
import ru.documents.review.api.dto.response.StaffRs;
import ru.documents.review.features.staff.model.StaffMember;
import ru.documents.review.features.staff.service.StaffService;
import ru.documents.review.features.workplace.model.Actor;

/**
 * Сотрудники.
 */
@RestController
public class StaffControllerImpl implements StaffController {

    private final StaffService staff;

    public StaffControllerImpl(StaffService staff) {
        this.staff = staff;
    }

    @Override
    public StaffRs list() {
        return new StaffRs(staff.list());
    }

    @Override
    public StaffMember create(StaffRq rq, Authentication auth) {
        return staff.create(rq.email(), rq.firstName(), rq.lastName(), rq.role(), Actor.of(auth).username());
    }

    @Override
    public void enabled(String login, EnabledRq rq, Authentication auth) {
        staff.setEnabled(login, rq.enabled(), Actor.of(auth).username());
    }
}
