--liquibase formatted sql

--changeset miroslavrov:20261009200000-worker-state-zone
-- требование: FR-08 — вместимость зоны (ACC-17): в какой зоне человек, счётчик «внутри» по зоне
alter table worker_state add column zone_id text;
create index worker_state_zone_inside_idx on worker_state (zone_id, last_in_at) where inside;
--rollback drop index worker_state_zone_inside_idx; alter table worker_state drop column zone_id;
