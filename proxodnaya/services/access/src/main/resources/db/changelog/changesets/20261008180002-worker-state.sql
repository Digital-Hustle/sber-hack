--liquibase formatted sql

--changeset b2:20261008180002-worker-state
-- Состояние рабочего в access: статус (проекция people.worker.v1) и присутствие на объекте.
-- Присутствие обновляется в той же транзакции, что и журнал прохода; по нему сервер выводит направление.
create table worker_state (
    worker_id    text primary key,
    status       text        not null default 'ACTIVE', -- ACTIVE | BLOCKED | DELETED
    inside       boolean     not null default false,
    last_in_at   timestamptz,
    last_out_at  timestamptz,
    updated_at   timestamptz not null
);
--rollback drop table worker_state;
