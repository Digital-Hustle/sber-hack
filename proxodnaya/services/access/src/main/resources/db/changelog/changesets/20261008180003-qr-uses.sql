--liquibase formatted sql

--changeset b2:20261008180003-qr-uses
-- Атомарное гашение QR: insert ... on conflict do nothing. 0 строк = QR_REUSED.
-- Колонка qr_window, а не window: window — зарезервированное слово PostgreSQL.
create table qr_uses (
    device_id   text        not null,
    qr_window   bigint      not null,
    used_at     timestamptz not null,
    attempt_id  uuid        not null,
    primary key (device_id, qr_window)
);
--rollback drop table qr_uses;
