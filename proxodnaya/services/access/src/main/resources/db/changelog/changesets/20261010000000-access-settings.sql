--liquibase formatted sql

--changeset b2:20261010000000-access-settings
-- Хранимые настройки прохода (изменяются через админку /api/v1/kiosk/settings).
create table access_settings (
    id            int primary key default 1,
    face_threshold      double precision not null default 0.6,
    qr_tolerance_sec    integer not null default 90,
    shift_grace_min     integer not null default 60,
    require_shift       boolean not null default true,
    temp_lock_failures  integer not null default 5,
    temp_lock_window_sec integer not null default 600,
    frames_required     integer not null default 3,
    challenge_ttl_sec   integer not null default 10,
    repeat_scan_cooldown_sec integer not null default 30,
    updated_at    timestamptz not null default now(),
    check (id = 1)
);
insert into access_settings (id) values (1) on conflict do nothing;
--rollback drop table access_settings;