--liquibase formatted sql

--changeset miroslavrov:20261009120200-history splitStatements:false
-- требование: B-08 (история append-only: UPDATE, DELETE и TRUNCATE запрещены триггером)
create table application_history (
    id uuid primary key,
    application_id uuid not null,
    document_id uuid,
    actor text not null,
    source text not null,
    action text not null,
    details jsonb not null default '{}',
    created_at timestamptz not null
);
create index application_history_app on application_history (application_id, created_at);
create function application_history_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'application_history is append-only';
end
$$;
create trigger application_history_no_change before update or delete on application_history
    for each row execute function application_history_append_only();
create trigger application_history_no_truncate before truncate on application_history
    for each statement execute function application_history_append_only();
--rollback drop table application_history; drop function application_history_append_only();
