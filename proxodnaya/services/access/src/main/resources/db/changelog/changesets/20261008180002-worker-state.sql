--liquibase formatted sql

--changeset b2:20261008180002-worker-state
-- Проекция people.worker.v1: статус рабочего для быстрой проверки в DecisionEngine.
create table worker_state (
    worker_id    text primary key,
    full_name    text        not null,
    status       text        not null default 'ACTIVE', -- ACTIVE | BLOCKED
    permit_until date        not null,
    zone_ids     text[]      not null default '{}',
    updated_at   timestamptz not null default now()
);
--rollback drop table worker_state;
