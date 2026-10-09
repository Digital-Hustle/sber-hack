--liquibase formatted sql

--changeset b2:20261008230000-zone-of-checkpoint
-- зона проходной (из people): проверка допуска в зону и интервалы присутствия в shift
alter table pass_attempts add column zone_id text;
alter table attempt_challenges add column zone_id text;
--rollback alter table attempt_challenges drop column zone_id; alter table pass_attempts drop column zone_id;
