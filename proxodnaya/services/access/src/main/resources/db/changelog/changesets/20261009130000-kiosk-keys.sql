--liquibase formatted sql

--changeset b2:20261009130000-kiosk-keys
-- Публичные ключи киосков для проверки подписи запросов (FR-64).
-- Киоск привязывается кодом сопряжения (people: POST /auth/kiosk/pair),
-- здесь — проекция для проверки X-Kiosk-Signature.
create table kiosk_keys (
    kiosk_id      text primary key,
    checkpoint_id text        not null,
    public_key    bytea       not null,
    status        text        not null default 'ACTIVE',
    updated_at    timestamptz not null default now()
);
--rollback drop table kiosk_keys;
