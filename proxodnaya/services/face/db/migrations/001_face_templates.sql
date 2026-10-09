-- Эталоны лиц: только зашифрованные эмбеддинги SFace, фото не храним (NFR-10).
CREATE TABLE face_templates (
    id            uuid PRIMARY KEY,
    worker_id     varchar(64)  NOT NULL,
    embedding_enc bytea        NOT NULL,
    nonce         bytea        NOT NULL,
    model_version varchar(32)  NOT NULL,
    quality       real         NOT NULL,
    created_by    varchar(128) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX face_templates_worker_idx ON face_templates (worker_id, created_at DESC);
