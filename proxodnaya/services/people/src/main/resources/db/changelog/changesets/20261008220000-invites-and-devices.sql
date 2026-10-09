--liquibase formatted sql

--changeset b1:20261008220000-invites-and-devices
create table invites (
    id         uuid primary key,
    worker_id  uuid        not null references workers (id),
    code_hash  text        not null unique,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    revoked_at timestamptz
);
create index invites_worker_idx on invites (worker_id);

create table worker_devices (
    device_id      text primary key check (device_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    worker_id      uuid        not null references workers (id),
    public_key_jwk jsonb       not null,
    status         text        not null check (status in ('ACTIVE', 'REVOKED')),
    bound_at       timestamptz not null,
    revoked_at     timestamptz
);
-- один действующий телефон на рабочего: новый телефон = перепривязка (старый REVOKED)
create unique index worker_devices_one_active_idx on worker_devices (worker_id) where status = 'ACTIVE';
--rollback drop table worker_devices; drop table invites;
