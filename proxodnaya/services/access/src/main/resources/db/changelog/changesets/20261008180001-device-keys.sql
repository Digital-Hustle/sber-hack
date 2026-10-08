--liquibase formatted sql

--changeset b2:20261008180001-device-keys
-- Проекция people.device.v1: публичные ключи устройств для проверки подписи QR без похода в people.
create table device_keys (
    device_id    text primary key,
    worker_id    text        not null,
    public_key   bytea       not null, -- X.509 SubjectPublicKeyInfo (DER), P-256
    status       text        not null default 'ACTIVE', -- ACTIVE | REVOKED
    updated_at   timestamptz not null
);
create index device_keys_worker_idx on device_keys (worker_id) where status = 'ACTIVE';
--rollback drop table device_keys;
