--liquibase formatted sql

--changeset miroslavrov:20261020120000-history-redaction splitStatements:false
-- требование: NFR-10 (удаление заявки обезличивает журнал), B-08 (история по-прежнему append-only)
-- Единственное разрешённое изменение записи — убрать значения полей и тексты оператора с пометкой redacted.
create or replace function application_history_append_only() returns trigger language plpgsql as $$
begin
    if tg_op = 'UPDATE'
       and new.id = old.id and new.application_id = old.application_id
       and new.document_id is not distinct from old.document_id
       and new.actor = old.actor and new.source = old.source and new.action = old.action
       and new.created_at = old.created_at
       and new.details = (old.details - array['from', 'to', 'modelValue', 'comment', 'items'])
                         || '{"redacted": true}'::jsonb then
        return new;
    end if;
    raise exception 'application_history is append-only';
end
$$;
--rollback create or replace function application_history_append_only() returns trigger language plpgsql as $$ begin raise exception 'application_history is append-only'; end $$;
