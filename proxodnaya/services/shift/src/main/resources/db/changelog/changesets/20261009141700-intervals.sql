--liquibase formatted sql

--changeset b3:20261009141700-intervals
-- проекция попыток access.pass.v1: аналитика отказов и нагрузки без синхронных запросов в access
create table pass_events (
    attempt_id    uuid primary key,
    worker_id     text,
    checkpoint_id text,
    zone_id       text,
    direction     text        not null,
    decision      text        not null,
    reason_code   text        not null,
    source        text        not null,
    ts            timestamptz not null
);
create index pass_events_ts_idx on pass_events (ts);
create index pass_events_worker_idx on pass_events (worker_id, ts);

create table presence_intervals (
    id               uuid primary key,
    worker_id        text        not null,
    zone_id          text,
    shift_id         uuid references shifts (id) on delete set null,
    entered_at       timestamptz not null,
    exited_at        timestamptz,
    close_reason     text,
    flags            text[]      not null default '{}',
    simulated        boolean     not null default false,
    enter_attempt_id uuid        not null,
    exit_attempt_id  uuid,
    constraint presence_intervals_order check (exited_at is null or exited_at >= entered_at),
    constraint presence_intervals_closed check ((exited_at is null) = (close_reason is null))
);
-- у рабочего не больше одного открытого интервала
create unique index presence_intervals_open_uq on presence_intervals (worker_id) where exited_at is null;
create index presence_intervals_worker_idx on presence_intervals (worker_id, entered_at desc);
create index presence_intervals_entered_idx on presence_intervals (entered_at desc, id desc);
--rollback drop table presence_intervals; drop table pass_events;
