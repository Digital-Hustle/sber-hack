--liquibase formatted sql

--changeset b1:20261008223000-sites-zones-permits
create table sites (
    id         uuid primary key,
    name       text        not null unique check (length(name) between 1 and 200),
    address    text check (length(address) <= 300),
    created_at timestamptz not null
);

create table zones (
    id         uuid primary key,
    site_id    uuid        not null references sites (id),
    name       text        not null check (length(name) between 1 and 200),
    capacity   int check (capacity is null or capacity > 0),
    created_at timestamptz not null,
    unique (site_id, name)
);

-- проходная ведёт в зону (zone_id); выход — из неё же
create table checkpoints (
    id         uuid primary key,
    zone_id    uuid        not null references zones (id),
    name       text        not null check (length(name) between 1 and 200),
    created_at timestamptz not null,
    unique (zone_id, name)
);

create table worker_zones (
    worker_id uuid not null references workers (id),
    zone_id   uuid not null references zones (id),
    primary key (worker_id, zone_id)
);

create table permits (
    worker_id   uuid not null references workers (id),
    type        text not null check (type in ('BRIEFING', 'MEDICAL')),
    valid_until date not null,
    primary key (worker_id, type)
);
--rollback drop table permits; drop table worker_zones; drop table checkpoints; drop table zones; drop table sites;
