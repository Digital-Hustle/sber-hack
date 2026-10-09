--liquibase formatted sql

--changeset miroslavrov:20261009193000-simulated-interval-chain
-- требование: FR-34 — интервалы симулятора отдельной цепочкой, не трогают реальный открытый интервал
drop index presence_intervals_open_uq;
create unique index presence_intervals_open_uq on presence_intervals (worker_id, simulated) where exited_at is null;
--rollback drop index presence_intervals_open_uq; create unique index presence_intervals_open_uq on presence_intervals (worker_id) where exited_at is null;
