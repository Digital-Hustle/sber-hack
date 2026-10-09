--liquibase formatted sql

--changeset b2:20261010000000-access-settings
-- требование: FR-34 (ACC-18) — настройки прохода из кабинета; одна строка, значения в jsonb (новые поля — без миграций),
-- чего нет в строке — берётся из env (application.yml access.*)
create table access_settings (
    id         smallint primary key default 1 check (id = 1),
    data       jsonb       not null,
    updated_at timestamptz not null,
    updated_by text
);
--rollback drop table access_settings;
