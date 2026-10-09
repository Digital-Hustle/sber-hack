--liquibase formatted sql

--changeset b3:20261009142000-exit-reminders
-- B-04: одно напоминание «отметь выход» на смену
create table exit_reminders (
    shift_id  uuid primary key references shifts (id) on delete cascade,
    worker_id text        not null,
    sent_at   timestamptz not null
);
--rollback drop table exit_reminders;
