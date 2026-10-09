--liquibase formatted sql

--changeset b3:20261009141800-workers
-- проекция people.worker.v1: ФИО, должность и подрядчик для табеля без синхронных запросов в people
create table workers (
    id         text primary key,
    full_name  text,
    position   text,
    contractor text,
    status     text        not null,
    updated_at timestamptz not null
);
create index workers_contractor_idx on workers (lower(contractor));
--rollback drop table workers;
