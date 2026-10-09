--liquibase formatted sql

--changeset b3:20261009141900-alerts
-- последний heartbeat телефона: для правил B-01 нужен только он
create table heartbeats (
    worker_id    text primary key,
    lat          double precision not null,
    lon          double precision not null,
    accuracy_m   double precision,
    distance_m   double precision,
    inside_fence boolean,
    at           timestamptz      not null
);

create table alerts (
    id          uuid primary key,
    type        text        not null,
    severity    text        not null,
    worker_id   text,
    zone_id     text,
    details     text        not null,
    dedup_key   text        not null,
    created_at  timestamptz not null,
    resolved_at timestamptz,
    acked_by    text,
    acked_at    timestamptz
);
-- одно активное оповещение на условие: пока оно держится, новое не создаётся
create unique index alerts_active_uq on alerts (dedup_key) where resolved_at is null;
create index alerts_created_idx on alerts (created_at desc, id desc);
--rollback drop table alerts; drop table heartbeats;
