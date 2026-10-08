--liquibase formatted sql

--changeset b2:20261008180004-pass-attempts
-- Журнал всех попыток прохода: append-only, hash-chain. Триггер запрещает UPDATE/DELETE.
create table pass_attempts (
    seq              bigint generated always as identity unique, -- порядок звеньев hash-chain
    id               uuid primary key,
    ts               timestamptz not null,
    checkpoint_id    text        not null,
    direction        text        not null,          -- IN | OUT
    claimed_worker_id text,
    device_id        text,
    qr_window        bigint,
    face_score       double precision,
    liveness_ok      boolean,
    challenge_action text,
    decision         text        not null,          -- ALLOW | DENY | MANUAL | ERROR
    reason_code      text        not null,
    details          jsonb,
    snapshot         bytea,
    latency_ms       integer,
    source           text        not null default 'KIOSK', -- KIOSK | MANUAL | OFFLINE | SIMULATOR
    prev_hash        text,
    hash             text        not null
);
create index pass_attempts_ts_idx          on pass_attempts (ts desc);
create index pass_attempts_worker_idx      on pass_attempts (claimed_worker_id, ts desc);
create index pass_attempts_checkpoint_idx  on pass_attempts (checkpoint_id, ts desc);
create index pass_attempts_decision_idx    on pass_attempts (decision, ts desc);
create index pass_attempts_device_window_idx on pass_attempts (device_id, qr_window);
--rollback drop table pass_attempts;

--changeset b2:20261008180004-pass-attempts-append-only splitStatements:false
-- Триггер: append-only. UPDATE и DELETE запрещены.
create or replace function prevent_pass_attempts_modify()
returns trigger as $$
begin
    raise exception 'pass_attempts is append-only (UPDATE/DELETE forbidden)';
end;
$$ language plpgsql;

create trigger pass_attempts_no_update
    before update on pass_attempts
    for each row execute function prevent_pass_attempts_modify();

create trigger pass_attempts_no_delete
    before delete on pass_attempts
    for each row execute function prevent_pass_attempts_modify();
--rollback drop trigger if exists pass_attempts_no_delete on pass_attempts;
--rollback drop trigger if exists pass_attempts_no_update on pass_attempts;
--rollback drop function if exists prevent_pass_attempts_modify;
