package ru.documents.intake.features.applications.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.exception.DomainException;
import ru.documents.intake.features.applications.model.ApplicationView;
import ru.documents.intake.features.applications.model.DocumentView;
import ru.documents.intake.features.applications.model.RequiredDocument;
import ru.documents.intake.features.applications.query.repository.ApplicationReadRepository;
import ru.documents.intake.features.applications.query.repository.ApplicationReadRepository.ActiveFile;
import ru.documents.intake.features.applications.query.repository.ApplicationReadRepository.Row;
import ru.documents.intake.features.applications.rules.RequirementsEngine;
import ru.documents.intake.features.applications.rules.RequirementsEngine.Rule;

/**
 * Заявка для кабинета и для review: набор, документы, чего не хватает (FR-15, B-01).
 */
@Service
public class ApplicationQueryService {

    private static final Set<String> DONE = Set.of("PARSED", "NEEDS_REVIEW");
    private static final Set<String> PROBLEM = Set.of("UNREADABLE", "FAILED");

    private final ApplicationReadRepository repo;
    private final RequirementsEngine engine;

    public ApplicationQueryService(ApplicationReadRepository repo, RequirementsEngine engine) {
        this.repo = repo;
        this.engine = engine;
    }

    /**
     * Заявка целиком.
     *
     * @param id заявка
     * @return представление
     */
    @Transactional(readOnly = true)
    public ApplicationView view(UUID id) {
        Row a = repo.application(id).orElseThrow(DomainException::notFound);
        List<DocumentView> docs = repo.documents(id);
        List<RequiredDocument> required = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (!a.profile().isEmpty()) {
            for (Rule r : engine.select(repo.rules(), a.profile())) {
                String state = state(r.type(), docs);
                required.add(new RequiredDocument(r.type(), r.title(), r.hint(), r.required(), state));
                if (r.required() && ("MISSING".equals(state) || "PROBLEM".equals(state))) {
                    missing.add(r.type());
                }
            }
        }
        return new ApplicationView(a.id(), a.status(), a.source(), a.profile(), required, docs, missing,
                repo.requests(id), a.decisionComment(), a.phone(), a.email(), a.createdAt(), a.updatedAt());
    }

    /**
     * Набор по ответам (без сохранения).
     *
     * @param answers ответы
     * @return правила
     */
    public List<Rule> requirements(java.util.Map<String, Object> answers) {
        return engine.select(repo.rules(), answers);
    }

    /**
     * Активные файлы документа (для docproc).
     *
     * @param docId документ
     * @return файлы
     */
    @Transactional(readOnly = true)
    public ApplicationReadRepository.DocumentFiles activeFiles(UUID docId) {
        return repo.activeFiles(docId);
    }

    static String state(String type, List<DocumentView> docs) {
        boolean processing = false;
        boolean problem = false;
        for (DocumentView d : docs) {
            String t = d.detectedType() != null ? d.detectedType() : d.slotType();
            if (!type.equals(t)) {
                continue;
            }
            if (DONE.contains(d.stage()) && type.equals(d.detectedType())) {
                return "DONE";
            }
            if (PROBLEM.contains(d.stage())) {
                problem = true;
            } else if (!DONE.contains(d.stage())) {
                processing = true;
            }
        }
        if (processing) {
            return "PROCESSING";
        }
        return problem ? "PROBLEM" : "MISSING";
    }
}
