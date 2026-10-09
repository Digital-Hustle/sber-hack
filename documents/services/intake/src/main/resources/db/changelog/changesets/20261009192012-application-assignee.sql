--liquibase formatted sql

--changeset miroslavrov:20261009192012-application-assignee
-- требование: B-07 (IN-14) — скан открывает только назначенный оператор или старший; назначение приходит из review
-- событием ApplicationAssigned (review.application.v1).
alter table applications add column assignee text;
--rollback alter table applications drop column assignee;
