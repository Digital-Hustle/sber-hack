--liquibase formatted sql

--changeset miroslavrov:20261009100200-documents-and-files
-- требование: FR-01, FR-03, FR-04, FR-62
create table documents (
    id uuid primary key,
    application_id uuid not null references applications (id) on delete cascade,
    slot_type text,
    stage text not null,
    detected_type text,
    reason_code text,
    reason_params jsonb not null default '{}'::jsonb,
    deleted boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index documents_app on documents (application_id) where not deleted;

create table document_files (
    id uuid primary key,
    document_id uuid not null references documents (id) on delete cascade,
    application_id uuid not null references applications (id) on delete cascade,
    version int not null,
    active boolean not null default true,
    sha256 text not null,
    phash bigint,
    mime text not null,
    size_bytes bigint not null,
    pages int not null,
    width int,
    height int,
    content_enc bytea not null,
    uploaded_at timestamptz not null
);
create index document_files_doc on document_files (document_id);
create index document_files_sha on document_files (application_id, sha256);

create table upload_requests (
    idempotency_key text not null,
    application_id uuid not null references applications (id) on delete cascade,
    response jsonb not null,
    created_at timestamptz not null,
    primary key (application_id, idempotency_key)
);

create table file_access_log (
    id bigserial primary key,
    file_id uuid not null,
    actor text not null,
    actor_kind text not null,
    accessed_at timestamptz not null
);
--rollback drop table file_access_log; drop table upload_requests; drop table document_files; drop table documents;
