--liquibase formatted sql

--changeset b1:20261008214800-workers
create table workers (
    id         uuid primary key,
    full_name  text        not null check (length(full_name) between 1 and 200),
    position   text        not null check (length(position) between 1 and 200),
    contractor text        not null check (length(contractor) between 1 and 200),
    status     text        not null check (status in ('ACTIVE', 'BLOCKED', 'DELETED')),
    created_at timestamptz not null,
    updated_at timestamptz not null
);
-- id — UUIDv7 (время в старших битах), курсор списка идёт по нему
create index workers_status_id_idx on workers (status, id desc);
--rollback drop table workers;
