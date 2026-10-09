package ru.documents.review.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;
import ru.documents.review.api.controller.AuthController;
import ru.documents.review.api.dto.request.LoginCodeRq;
import ru.documents.review.features.staff.model.AuthMode;
import ru.documents.review.features.staff.model.CodeSent;
import ru.documents.review.features.staff.model.TestCode;
import ru.documents.review.features.staff.service.LoginCodeService;

/**
 * Вход по коду из письма.
 */
@RestController
public class AuthControllerImpl implements AuthController {

    private final LoginCodeService codes;

    public AuthControllerImpl(LoginCodeService codes) {
        this.codes = codes;
    }

    @Override
    public CodeSent requestCode(LoginCodeRq rq) {
        return codes.request(rq.email());
    }

    @Override
    public AuthMode mode() {
        return codes.mode();
    }

    @Override
    public TestCode testCode(LoginCodeRq rq) {
        return new TestCode(codes.testCode(rq.email()));
    }
}
