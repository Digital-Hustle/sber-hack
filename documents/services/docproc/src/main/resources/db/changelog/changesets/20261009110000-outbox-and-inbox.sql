--liquibase formatted sql

--changeset miroslavrov:20261009110000-outbox-and-inbox
-- требование: NFR-17
create table outbox (
    id uuid primary key,
    topic text not null,
    msg_key text not null,
    payload jsonb not null,
    created_at timestamptz not null,
    published_at timestamptz
);
create index outbox_unpublished on outbox (created_at) where published_at is null;
create table processed_events (
    event_id text primary key,
    processed_at timestamptz not null
);
--rollback drop table processed_events; drop table outbox;
