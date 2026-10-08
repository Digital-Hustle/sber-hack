--liquibase formatted sql

--changeset b2:20261008180003-qr-uses
-- Атомарное гашение QR: insert ... on conflict do nothing. 0 строк = QR_REUSED.
create table qr_uses (
    device_id   text        not null,
    window      bigint       not null,
    used_at     timestamptz not null default now(),
    attempt_id  uuid        not null,
    primary key (device_id, window)
);
--rollback drop table qr_uses;
