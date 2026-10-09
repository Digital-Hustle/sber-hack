package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.documents.review.api.dto.request.LoginCodeRq;
import ru.documents.review.features.staff.model.AuthMode;
import ru.documents.review.features.staff.model.CodeSent;
import ru.documents.review.features.staff.model.TestCode;

/**
 * Вход сотрудника по коду из письма (без JWT): код проверяет Keycloak, сюда — только «пришлите код».
 */
@Tag(name = "auth")
@RequestMapping("/api/v1/auth")
public interface AuthController {

    /** Прислать код на почту; ответ одинаковый для любой почты. */
    @Operation(summary = "Прислать код входа на почту")
    @PostMapping("/code")
    CodeSent requestCode(@Valid @RequestBody LoginCodeRq rq);

    /** Режим стенда: в dev — список тестовых сотрудников для быстрого входа. */
    @Operation(summary = "Режим стенда (dev/prod)")
    @GetMapping("/mode")
    AuthMode mode();

    /** Только APP_MODE=dev: код сразу в ответе. В prod — 404 TEST_MODE_OFF. */
    @Operation(summary = "Тестовый код входа (только dev)")
    @PostMapping("/test-code")
    TestCode testCode(@Valid @RequestBody LoginCodeRq rq);
}
