--liquibase formatted sql

--changeset b2:20261008180005-attempt-challenges
-- Попытка между шагами «QR → челлендж» и «кадры → решение». Токен одноразовый: забирается delete ... returning.
create table attempt_challenges (
    token            uuid primary key,
    worker_id        text        not null,
    device_id        text        not null,
    qr_window        bigint      not null,
    direction        text        not null, -- IN | OUT, выведено сервером
    checkpoint_id    text        not null,
    action           text        not null, -- TURN_LEFT | TURN_RIGHT | BLINK | NOD
    frames_required  integer     not null,
    started_at       timestamptz not null,
    expires_at       timestamptz not null
);
create index attempt_challenges_expires_idx on attempt_challenges (expires_at);
--rollback drop table attempt_challenges;
