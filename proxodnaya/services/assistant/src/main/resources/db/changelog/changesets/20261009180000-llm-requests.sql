--liquibase formatted sql

--changeset b3:20261009180000-llm-requests
-- FR-46: журнал вызовов модели; вопрос — только хэшем (ПДн в журнал не пишем)
create table llm_requests (
    id            uuid primary key,
    ask_id        uuid        not null,
    ts            timestamptz not null,
    provider      text        not null,
    model         text        not null,
    latency_ms    integer     not null,
    tokens_in     integer,
    tokens_out    integer,
    tool_calls    integer     not null default 0,
    validation    text,
    error         text,
    question_hash text        not null
);
create index llm_requests_ts_idx on llm_requests (ts desc);
--rollback drop table llm_requests;
