package ru.documents.intake.features.applications.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.event.Topics;
import ru.documents.common.exception.BaseExceptionHandler;
import ru.documents.common.exception.DomainException;
import ru.documents.common.outbox.OutboxWriter;
import ru.documents.intake.config.IntakeProperties;
import ru.documents.intake.features.applications.command.repository.ApplicationRepository;
import ru.documents.intake.features.applications.model.Created;
import ru.documents.intake.features.applications.query.service.ApplicationQueryService;
import ru.documents.intake.features.applications.rules.Questionnaire;
import ru.documents.intake.features.applications.rules.RequirementsEngine.Rule;
import ru.documents.intake.shared.Secrets;

/**
 * Жизненный цикл заявки: создание с согласием, анкета, отправка, возврат по коду, билеты SSE.
 */
@Service
public class ApplicationCommandService {

    /** Международный номер: «+» и 10–15 цифр (E.164). */
    private static final Pattern VALID_PHONE = Pattern.compile("^\\+\\d{10,15}$");
    private static final Pattern VALID_EMAIL =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final int MAX_EMAIL = 120;

    private static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final Duration RESUME_WINDOW = Duration.ofMinutes(15);
    private static final String APP_ID = "applicationId";
    private static final String DELETED = "DELETED";

    private final ApplicationRepository repo;
    private final ApplicationQueryService query;
    private final Questionnaire questionnaire;
    private final OutboxWriter outbox;
    private final IntakeProperties props;
    private final ObjectMapper json;
    private final Clock clock;

    public ApplicationCommandService(ApplicationRepository repo, ApplicationQueryService query,
                                     Questionnaire questionnaire, OutboxWriter outbox, IntakeProperties props,
                                     ObjectMapper json, Clock clock) {
        this.repo = repo;
        this.query = query;
        this.questionnaire = questionnaire;
        this.outbox = outbox;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Новая заявка (FR-14): без согласия — CONSENT_REQUIRED.
     *
     * @param consent согласие на обработку ПДн
     * @param phone   телефон
     * @param email   почта
     * @param demo    демо-заявка (source=DEMO)
     * @return ключ и код (один раз)
     */
    @Transactional
    public Created create(boolean consent, String phone, String email, boolean demo) {
        if (!consent) {
            throw DomainException.badRequest("CONSENT_REQUIRED", "Нужно согласие на обработку данных",
                    "Отметьте согласие");
        }
        String p = normalizePhone(phone);
        String e = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        if (p != null && !VALID_PHONE.matcher(p).matches()) {
            throw new DomainException("VALIDATION_ERROR", 400, "Неверный номер телефона",
                    "Укажите номер полностью, например +7 900 000-00-00");
        }
        if (e != null && (e.length() > MAX_EMAIL || !VALID_EMAIL.matcher(e).matches())) {
            throw new DomainException("VALIDATION_ERROR", 400, "Неверный адрес почты", "Например, name@mail.ru");
        }
        UUID id = UuidCreator.getTimeOrderedEpoch();
        String key = Secrets.accessKey();
        String code = Secrets.resumeCode();
        Instant now = clock.instant();
        String source = demo ? "DEMO" : "WEB";
        repo.insertApplication(id, Secrets.hash(props.pepper(), key), Secrets.hash(props.pepper(), code), p, e,
                source, now);
        outbox.add(Topics.INTAKE_APPLICATION_V1, "ApplicationCreated", id.toString(),
                Map.of(APP_ID, id.toString(), "source", source, "createdAt", now.toString()));
        return new Created(id, key, code, link(id, key));
    }

    /**
     * Сохранить анкету и пересчитать набор (FR-10).
     *
     * @param id      заявка
     * @param answers ответы
     * @return набор
     */
    @Transactional
    public List<Rule> saveProfile(UUID id, Map<String, Object> answers) {
        String status = repo.status(id).orElseThrow(DomainException::notFound);
        ensureEditable(status);
        questionnaire.validate(answers);
        Map<String, Object> clean = new HashMap<>();
        questionnaire.questions().forEach(q -> {
            if (answers.containsKey(q.id())) {
                clean.put(q.id(), answers.get(q.id()));
            }
        });
        List<Rule> rules = query.requirements(clean);
        repo.updateProfile(id, write(clean), clock.instant());
        outbox.add(Topics.INTAKE_APPLICATION_V1, "ProfileChanged", id.toString(), Map.of(APP_ID, id.toString(),
                "profile", clean, "requiredTypes", rules.stream().map(Rule::type).toList()));
        return rules;
    }

    /**
     * Отправить комплект оператору.
     *
     * @param id заявка
     * @return каких обязательных документов нет
     */
    @Transactional
    public List<String> submit(UUID id) {
        String status = repo.status(id).orElseThrow(DomainException::notFound);
        ensureEditable(status);
        List<String> missing = query.view(id).missing();
        repo.setStatus(id, "SUBMITTED", null, clock.instant());
        outbox.add(Topics.INTAKE_APPLICATION_V1, "Submitted", id.toString(),
                Map.of(APP_ID, id.toString(), "missing", missing));
        return missing;
    }

    /**
     * Удалить заявку или отозвать согласие (NFR-10, 152-ФЗ) — в любом статусе. Файлы всех версий стёрты, анкета и
     * контакты очищены, ключ и код больше не подходят; docproc и review по событиям удаляют результаты и поля,
     * в журнале review остаётся обезличенная запись.
     *
     * @param id заявка
     */
    @Transactional
    public void delete(UUID id) {
        if (DELETED.equals(repo.status(id).orElseThrow(DomainException::notFound))) {
            return;
        }
        Instant now = clock.instant();
        for (UUID doc : repo.liveDocuments(id)) {
            repo.deleteDocument(doc, now);
            outbox.add(Topics.INTAKE_DOCUMENT_V1, "DocumentRemoved", id.toString(),
                    Map.of("documentId", doc.toString(), APP_ID, id.toString()));
        }
        repo.erase(id, Secrets.hash(props.pepper(), "deleted:" + Secrets.accessKey()), now);
        outbox.add(Topics.INTAKE_APPLICATION_V1, "ApplicationDeleted", id.toString(), Map.of(APP_ID, id.toString()));
    }

    /**
     * Продолжить с другого устройства: контакт + код → новый ключ (5 попыток, затем 429).
     *
     * @param contact телефон или почта
     * @param code    код возврата
     * @return новая ссылка
     */
    @Transactional(noRollbackFor = DomainException.class)
    public Created resume(String contact, String code) {
        String c = contact == null ? "" : contact.trim();
        String normalized = c.contains("@") ? c.toLowerCase() : normalizePhone(c);
        String contactHash = Secrets.hash(props.pepper(), "resume:" + normalized);
        Instant now = clock.instant();
        Instant window = now.minus(RESUME_WINDOW);
        if (repo.resumeFailures(contactHash, window) >= props.resumeMaxAttempts()) {
            throw new DomainException("RATE_LIMITED", 429, "Слишком много попыток", "Подождите 15 минут",
                    Map.of(BaseExceptionHandler.RETRY_AFTER_SEC, RESUME_WINDOW.toSeconds()));
        }
        String codeHash = Secrets.hash(props.pepper(), code == null ? "" : code.trim().toUpperCase());
        List<UUID> found = normalized == null ? List.of() : repo.byContact(normalized, codeHash);
        if (found.isEmpty()) {
            repo.resumeFailed(contactHash, now, window);
            throw DomainException.badRequest("RESUME_INVALID", "Код или контакт не подходят",
                    "Проверьте код из ссылки-приглашения");
        }
        repo.resumeReset(contactHash);
        UUID id = found.get(0);
        String key = Secrets.accessKey();
        repo.rotateKey(id, Secrets.hash(props.pepper(), key), now);
        return new Created(id, key, null, link(id, key));
    }

    /**
     * Одноразовый билет SSE на 30 секунд.
     *
     * @param id заявка
     * @return билет
     */
    @Transactional
    public String ticket(UUID id) {
        String t = Secrets.accessKey();
        repo.insertTicket(Secrets.hash(props.pepper(), t), id, clock.instant().plus(TICKET_TTL));
        return t;
    }

    /**
     * Погасить билет SSE.
     *
     * @param ticket билет
     * @param id     заявка из пути
     */
    @Transactional
    public void consumeTicket(String ticket, UUID id) {
        UUID app = repo.consumeTicket(Secrets.hash(props.pepper(), ticket == null ? "" : ticket), clock.instant())
                .orElseThrow(DomainException::notFound);
        if (!app.equals(id)) {
            throw DomainException.notFound();
        }
    }

    static void ensureEditable(String status) {
        if (DELETED.equals(status)) {
            throw DomainException.notFound();
        }
        if ("IN_REVIEW".equals(status) || "ACCEPTED".equals(status)) {
            throw DomainException.conflict("APPLICATION_LOCKED", "Заявка уже на проверке у оператора",
                    "Дождитесь ответа оператора");
        }
    }

    private String link(UUID id, String key) {
        return props.publicUrl().replaceAll("/+$", "") + "/apply/" + id + "#k=" + key;
    }

    static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String d = phone.replaceAll("\\D", "");
        if (d.length() == 11 && d.startsWith("8")) {
            d = "7" + d.substring(1);
        }
        return "+" + d;
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
