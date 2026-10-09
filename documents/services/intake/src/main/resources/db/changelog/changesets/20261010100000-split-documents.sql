--liquibase formatted sql

--changeset agent:20261010100000-split-documents
-- требование: FR-07 (IN-12) — документ, выделенный из многостраничного файла: файлы берутся у исходного документа,
-- в разбор идут только его страницы (сквозные номера по активным файлам исходного документа).
alter table documents add column source_document_id uuid references documents (id) on delete cascade;
alter table documents add column only_pages int[];
alter table documents add column split_type text;
create unique index documents_split on documents (source_document_id, split_type)
    where source_document_id is not null and not deleted;
--rollback drop index documents_split; alter table documents drop column split_type; alter table documents drop column only_pages; alter table documents drop column source_document_id;
