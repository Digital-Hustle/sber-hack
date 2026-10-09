--liquibase formatted sql

--changeset b3:20261009141600-shifts
create table shift_templates (
    id         uuid primary key,
    name       text        not null,
    start_time time        not null,
    end_time   time        not null,
    created_at timestamptz not null,
    constraint shift_templates_name_uq unique (name),
    constraint shift_templates_not_empty check (start_time <> end_time)
);

create table shifts (
    id          uuid primary key,
    worker_id   text        not null,
    starts_at   timestamptz not null,
    ends_at     timestamptz not null,
    template_id uuid references shift_templates (id) on delete set null,
    created_by  text        not null,
    created_at  timestamptz not null,
    constraint shifts_positive check (ends_at > starts_at),
    constraint shifts_max_24h check (ends_at - starts_at <= interval '24 hours')
);
-- пересечение смен одного рабочего проверяет приложение под advisory-блокировкой рабочего
create index shifts_worker_idx on shifts (worker_id, starts_at);
create index shifts_starts_idx on shifts (starts_at, id);
--rollback drop table shifts; drop table shift_templates;
