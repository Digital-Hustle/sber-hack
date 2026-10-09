--liquibase formatted sql

--changeset miroslavrov:20261009220000-staff
-- требование: FR-60/61 (PPL-13, ADR-041) — персонал, вход по коду из письма, сессии, журнал доступа
create table staff_users (
    id           uuid primary key,
    name         text        not null,
    email        text        not null,
    role         text        not null check (role in ('ADMIN', 'SECURITY_OFFICER', 'MANAGER', 'INSTALLER', 'GUARD')),
    status       text        not null check (status in ('ACTIVE', 'INVITED', 'DISABLED')),
    created_at   timestamptz not null,
    last_seen_at timestamptz
);
create unique index staff_users_email_uq on staff_users (lower(email));

-- один активный код на адрес; строка заводится и для неизвестного адреса (code_hash null) — ответ не раскрывает, есть ли пользователь
create table staff_login_codes (
    email      text primary key,
    code_hash  text,
    salt       text        not null,
    attempts   int         not null default 0,
    expires_at timestamptz not null,
    resend_at  timestamptz not null
);

create table staff_sessions (
    id           uuid primary key,
    staff_id     uuid        not null references staff_users (id),
    refresh_hash text        not null unique,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    revoked_at   timestamptz
);
create index staff_sessions_staff_idx on staff_sessions (staff_id);

create table staff_access_log (
    id        uuid primary key,
    ts        timestamptz not null,
    by_id     uuid,
    by_name   text,
    target_id uuid,
    target    text,
    action    text        not null,
    from_role text,
    to_role   text,
    code      text
);
--rollback drop table staff_access_log; drop table staff_sessions; drop table staff_login_codes; drop table staff_users;
