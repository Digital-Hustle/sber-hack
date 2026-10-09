--liquibase formatted sql

--changeset miroslavrov:20261009110100-jobs-and-results
-- требование: FR-20, FR-26, FR-31, FR-43 (очередь в БД, стадии, результаты, журнал модели)
create table jobs (
    id uuid primary key,
    document_id uuid not null unique,
    application_id uuid not null,
    file_version int not null default 1,
    step text not null,
    stage text not null,
    status text not null,
    attempts int not null default 0,
    next_run_at timestamptz not null,
    lease_until timestamptz,
    worker text,
    last_error_code text,
    forced_type text,
    vision boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    started_at timestamptz,
    finished_at timestamptz
);
create index jobs_ready on jobs (next_run_at) where status = 'READY';
create index jobs_running on jobs (lease_until) where status = 'RUNNING';

create table stage_events (
    id bigserial primary key,
    document_id uuid not null,
    application_id uuid not null,
    stage text not null,
    reason_code text,
    duration_ms bigint,
    at timestamptz not null
);
create index stage_events_doc on stage_events (document_id, at);
create index stage_events_at on stage_events (at);

create table stage_outputs (
    document_id uuid not null,
    stage text not null,
    payload jsonb not null,
    created_at timestamptz not null,
    primary key (document_id, stage)
);

create table parse_results (
    id uuid primary key,
    document_id uuid not null,
    application_id uuid not null,
    current boolean not null,
    stage text not null,
    type text not null,
    type_confidence double precision,
    evidence jsonb,
    reasons jsonb not null default '[]',
    quality jsonb,
    mrz jsonb,
    pages jsonb,
    model text,
    prompt_version text,
    schema_version int,
    durations jsonb,
    created_at timestamptz not null
);
create index parse_results_doc on parse_results (document_id, created_at desc);
create unique index parse_results_current on parse_results (document_id) where current;

create table fields (
    result_id uuid not null references parse_results (id) on delete cascade,
    name text not null,
    title text not null,
    sort int not null,
    value text,
    raw text,
    page int,
    bbox jsonb,
    confidence double precision,
    checks jsonb not null default '[]',
    reason text,
    primary key (result_id, name)
);

create table llm_calls (
    id bigserial primary key,
    document_id text,
    stage text,
    provider text,
    model text,
    latency_ms bigint,
    tokens_in int,
    tokens_out int,
    schema_valid boolean,
    outcome text not null,
    created_at timestamptz not null
);
create index llm_calls_doc on llm_calls (document_id);
--rollback drop table llm_calls; drop table fields; drop table parse_results; drop table stage_outputs; drop table stage_events; drop table jobs;
