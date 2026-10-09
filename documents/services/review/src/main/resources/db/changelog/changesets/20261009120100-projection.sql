--liquibase formatted sql

--changeset miroslavrov:20261009120100-projection
-- требование: FR-51, FR-52 (проекция заявок и документов из событий intake и docproc), FR-53
create table applications (
    id uuid primary key,
    status text not null default 'DRAFT',
    source text,
    required_types jsonb not null default '[]',
    missing jsonb not null default '[]',
    applicant_name text,
    assignee text,
    assigned_at timestamptz,
    created_at timestamptz not null,
    submitted_at timestamptz,
    decided_at timestamptz,
    updated_at timestamptz not null
);
create index applications_queue on applications (status, submitted_at);
create table documents (
    id uuid primary key,
    application_id uuid not null,
    slot_type text,
    type text,
    type_confidence double precision,
    stage text not null,
    reason_code text,
    reasons jsonb,
    result jsonb,
    result_id text,
    expires_on date,
    confirmed_by text,
    confirmed_at timestamptz,
    removed boolean not null default false,
    received_at timestamptz not null,
    parsed_at timestamptz,
    updated_at timestamptz not null
);
create index documents_app on documents (application_id);
create index documents_expiring on documents (expires_on) where not removed;
create table field_overrides (
    id uuid primary key,
    document_id uuid not null,
    name text not null,
    value text,
    author text not null,
    created_at timestamptz not null
);
create index field_overrides_doc on field_overrides (document_id, name, created_at);
create table decisions (
    id uuid primary key,
    application_id uuid not null,
    decision text not null,
    comment text,
    actor text not null,
    created_at timestamptz not null
);
create index decisions_app on decisions (application_id, created_at);
create table stream_tickets (
    ticket_hash text primary key,
    username text not null,
    expires_at timestamptz not null
);
--rollback drop table stream_tickets; drop table decisions; drop table field_overrides; drop table documents; drop table applications;
