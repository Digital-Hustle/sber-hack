--liquibase formatted sql

--changeset miroslavrov:20261009215821-kiosks
-- требование: FR-64 (ACC-12) — терминалы проходной: ключ терминала, код сопряжения, привязка к проходной и логике работы
create table kiosks (
    id             uuid primary key,
    public_key     text        not null,
    pair_code      text unique,
    name           text,
    checkpoint_id  text,
    mode           text,
    offline_policy text,
    created_at     timestamptz not null,
    paired_at      timestamptz,
    paired_by      text,
    last_seen      timestamptz
);
-- одноразовые nonce подписанных запросов: повтор того же запроса → отказ; живут дольше окна времени
create table kiosk_nonces (
    kiosk_id   uuid        not null references kiosks (id) on delete cascade,
    nonce      text        not null,
    expires_at timestamptz not null,
    primary key (kiosk_id, nonce)
);
create index kiosk_nonces_expires_idx on kiosk_nonces (expires_at);
--rollback drop table kiosk_nonces; drop table kiosks;
