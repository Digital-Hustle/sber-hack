package ru.documents.intake.features.applications.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.event.Topics;
import ru.documents.common.exception.DomainException;
import ru.documents.common.outbox.OutboxWriter;
import ru.documents.intake.config.IntakeProperties;
import ru.documents.intake.features.applications.command.repository.ApplicationRepository;
import ru.documents.intake.features.applications.command.repository.ApplicationRepository.Phash;
import ru.documents.intake.features.applications.model.Inspection;
import ru.documents.intake.features.applications.model.Uploaded;
import ru.documents.intake.shared.Codes;

/**
 * Приём файла (FR-01..FR-06): проверки → файл (AES-GCM) + документ RECEIVED + событие в outbox одной транзакцией.
 * Разбор здесь не запускается — только событие для очереди docproc (Д7).
 */
@Service
public class UploadService {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final String APP_ID = "applicationId";
    private static final String DOC_ID = "documentId";

    private final ApplicationRepository repo;
    private final FileInspector inspector;
    private final FileCrypto crypto;
    private final OutboxWriter outbox;
    private final IntakeProperties props;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final Clock clock;

    public UploadService(ApplicationRepository repo, FileInspector inspector, FileCrypto crypto, OutboxWriter outbox,
                         IntakeProperties props, ObjectMapper json, MeterRegistry meters, Clock clock) {
        this.repo = repo;
        this.inspector = inspector;
        this.crypto = crypto;
        this.outbox = outbox;
        this.props = props;
        this.json = json;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Новый документ.
     *
     * @param appId          заявка
     * @param bytes          файл
     * @param slotType       пункт (только для показа, в разбор не идёт)
     * @param idempotencyKey ключ идемпотентности
     * @return принятый файл
     */
    @Transactional(noRollbackFor = DomainException.class)
    public Uploaded upload(UUID appId, byte[] bytes, String slotType, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        var prev = repo.idempotent(appId, key);
        if (prev.isPresent()) {
            return read(prev.get());
        }
        prepare(appId);
        Inspection ins = check(bytes);
        repo.duplicate(appId, ins.sha256()).ifPresent(d -> {
            throw new DomainException("DUPLICATE_FILE", 409, "Этот файл уже загружен (" + title(d.type()) + ")",
                    "Загрузите другой документ", Map.of("duplicateOf", d.documentId().toString()));
        });
        List<String> warnings = new ArrayList<>();
        UUID near = nearDuplicate(appId, ins);
        if (near != null) {
            warnings.add("NEAR_DUPLICATE");
        }
        Instant now = clock.instant();
        UUID docId = UuidCreator.getTimeOrderedEpoch();
        UUID fileId = UuidCreator.getTimeOrderedEpoch();
        repo.insertDocument(docId, appId, blankToNull(slotType), now);
        repo.insertFile(fileId, docId, appId, 1, ins.sha256(), ins.phash(), ins.mime(), bytes.length, ins.pages(),
                ins.width(), ins.height(), crypto.encrypt(bytes), now);
        repo.resolveRequests(appId, docId, blankToNull(slotType), now);
        repo.touch(appId, now);
        Map<String, Object> payload = new HashMap<>();
        payload.put(DOC_ID, docId.toString());
        payload.put(APP_ID, appId.toString());
        payload.put("fileId", fileId.toString());
        payload.put("version", 1);
        if (slotType != null && !slotType.isBlank()) {
            payload.put("slotType", slotType);
        }
        outbox.add(Topics.INTAKE_DOCUMENT_V1, "DocumentReceived", appId.toString(), payload);
        Uploaded rs = new Uploaded(docId, fileId, "RECEIVED", near, warnings);
        repo.saveIdempotent(appId, key, write(rs), now);
        return rs;
    }

    /**
     * Дозагрузка страницы или замена файла (FR-04): версии хранятся, документ разбирается заново.
     *
     * @param appId          заявка
     * @param docId          документ
     * @param bytes          файл
     * @param replace        заменить (иначе добавить страницу)
     * @param idempotencyKey ключ идемпотентности
     * @return принятый файл
     */
    @Transactional(noRollbackFor = DomainException.class)
    public Uploaded addFile(UUID appId, UUID docId, byte[] bytes, boolean replace, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        var prev = repo.idempotent(appId, key);
        if (prev.isPresent()) {
            return read(prev.get());
        }
        UUID owner = repo.documentApplication(docId).orElseThrow(DomainException::notFound);
        if (!owner.equals(appId)) {
            throw DomainException.notFound();
        }
        prepare(appId);
        Inspection ins = check(bytes);
        repo.duplicate(appId, ins.sha256()).ifPresent(d -> {
            throw new DomainException("DUPLICATE_FILE", 409, "Этот файл уже загружен (" + title(d.type()) + ")",
                    "Загрузите другой документ", Map.of("duplicateOf", d.documentId().toString()));
        });
        Instant now = clock.instant();
        if (replace) {
            repo.deactivateFiles(docId);
        }
        UUID fileId = UuidCreator.getTimeOrderedEpoch();
        int version = repo.nextVersion(docId);
        repo.insertFile(fileId, docId, appId, version, ins.sha256(), ins.phash(), ins.mime(), bytes.length,
                ins.pages(), ins.width(), ins.height(), crypto.encrypt(bytes), now);
        repo.resetStage(docId, now);
        removeChildren(appId, docId);
        repo.resolveRequests(appId, docId, repo.slot(docId).orElse(null), now);
        repo.touch(appId, now);
        outbox.add(Topics.INTAKE_DOCUMENT_V1, "FilesChanged", appId.toString(), Map.of(DOC_ID, docId.toString(),
                APP_ID, appId.toString(), "fileId", fileId.toString(), "version", version,
                "mode", replace ? "replace" : "add"));
        Uploaded rs = new Uploaded(docId, fileId, "RECEIVED", null, List.of());
        repo.saveIdempotent(appId, key, write(rs), now);
        return rs;
    }

    /**
     * Удалить документ до решения оператора.
     *
     * @param appId заявка
     * @param docId документ
     */
    @Transactional
    public void delete(UUID appId, UUID docId) {
        UUID owner = repo.documentApplication(docId).orElseThrow(DomainException::notFound);
        if (!owner.equals(appId)) {
            throw DomainException.notFound();
        }
        ApplicationCommandService.ensureEditable(repo.status(appId).orElseThrow(DomainException::notFound));
        removeChildren(appId, docId);
        repo.deleteDocument(docId, clock.instant());
        outbox.add(Topics.INTAKE_DOCUMENT_V1, "DocumentRemoved", appId.toString(),
                Map.of(DOC_ID, docId.toString(), APP_ID, appId.toString()));
    }

    /** Файлы исходного документа поменялись или он удалён — выделенные из него документы больше не верны (FR-07). */
    private void removeChildren(UUID appId, UUID docId) {
        for (UUID child : repo.children(docId)) {
            repo.deleteDocument(child, clock.instant());
            outbox.add(Topics.INTAKE_DOCUMENT_V1, "DocumentRemoved", appId.toString(),
                    Map.of(DOC_ID, child.toString(), APP_ID, appId.toString()));
        }
    }

    private void prepare(UUID appId) {
        ApplicationCommandService.ensureEditable(repo.status(appId).orElseThrow(DomainException::notFound));
        if (repo.uploadsSince(appId, clock.instant().minus(RATE_WINDOW)) >= props.uploadsPerMinute()) {
            throw reject("RATE_LIMITED", 429, "Слишком много загрузок подряд", "Подождите минуту");
        }
        if (repo.activeFiles(appId) >= props.maxFiles()) {
            throw reject("TOO_MANY_FILES", 422, "В заявке уже " + props.maxFiles() + " файлов", "Удалите лишние файлы");
        }
    }

    private Inspection check(byte[] bytes) {
        try {
            return inspector.inspect(bytes);
        } catch (DomainException e) {
            meters.counter("intake_rejects_total", "code", e.code()).increment();
            throw e;
        }
    }

    private DomainException reject(String code, int status, String detail, String hint) {
        meters.counter("intake_rejects_total", "code", code).increment();
        return Codes.reject(code, status, detail, hint);
    }

    private UUID nearDuplicate(UUID appId, Inspection ins) {
        if (ins.phash() == null) {
            return null;
        }
        for (Phash p : repo.phashes(appId)) {
            if (PerceptualHash.distance(p.phash(), ins.phash()) <= PerceptualHash.NEAR_DUPLICATE_BITS) {
                return p.documentId();
            }
        }
        return null;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw DomainException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Нет заголовка Idempotency-Key",
                    "Обновите страницу и повторите");
        }
        return key;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String title(String type) {
        return type == null ? "документ без пункта" : DocTitles.of(type);
    }

    private String write(Uploaded u) {
        try {
            return json.writeValueAsString(u);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Uploaded read(String s) {
        try {
            return json.readValue(s, Uploaded.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
