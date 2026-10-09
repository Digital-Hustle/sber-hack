package ru.documents.docproc.features.processing.query.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ru.documents.docproc.features.processing.model.DocType;
import ru.documents.docproc.features.processing.query.repository.DocTypeRepository;

/**
 * Реестр видов документов (данные doc_types, ADR-010).
 */
@Service
public class TypeRegistry {

    private final DocTypeRepository repo;

    public TypeRegistry(DocTypeRepository repo) {
        this.repo = repo;
    }

    /** Все виды. */
    public List<DocType> all() {
        return repo.all();
    }

    /** Вид по коду. */
    public Optional<DocType> get(String code) {
        return repo.all().stream().filter(t -> t.code().equals(code)).findFirst();
    }
}
