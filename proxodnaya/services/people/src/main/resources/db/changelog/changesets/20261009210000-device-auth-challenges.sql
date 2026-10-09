--liquibase formatted sql

--changeset miroslavrov:20261009210000-device-auth-challenges
-- требование: FR-63 (PPL-05) — одноразовый challenge для device-токена телефона
create table device_auth_challenges (
    challenge  text primary key,
    device_id  text        not null,
    expires_at timestamptz not null,
    used_at    timestamptz
);
create index device_auth_challenges_expires_idx on device_auth_challenges (expires_at);
--rollback drop table device_auth_challenges;
