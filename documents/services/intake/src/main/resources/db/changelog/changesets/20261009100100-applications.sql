--liquibase formatted sql

--changeset miroslavrov:20261009100100-applications
-- требование: FR-10, FR-14
create table applications (
    id uuid primary key,
    status text not null default 'DRAFT',
    profile jsonb not null default '{}'::jsonb,
    access_key_hash text not null unique,
    resume_code_hash text not null,
    contact_phone text,
    contact_email text,
    source text not null default 'WEB',
    consent_at timestamptz not null,
    decision_comment text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index applications_phone on applications (contact_phone);
create index applications_email on applications (lower(contact_email));

create table resume_attempts (
    contact_hash text primary key,
    failures int not null default 0,
    window_start timestamptz not null
);

create table doc_requirements (
    id serial primary key,
    doc_type text not null,
    title text not null,
    hint text not null,
    condition jsonb not null,
    required boolean not null default true,
    sort int not null default 0
);

create table operator_requests (
    id uuid primary key,
    application_id uuid not null references applications (id) on delete cascade,
    document_id uuid,
    doc_type text,
    reason text not null,
    created_at timestamptz not null,
    resolved_at timestamptz
);
create index operator_requests_app on operator_requests (application_id);

create table stream_tickets (
    ticket_hash text primary key,
    application_id uuid not null references applications (id) on delete cascade,
    expires_at timestamptz not null
);
--rollback drop table stream_tickets; drop table operator_requests; drop table doc_requirements; drop table resume_attempts; drop table applications;
