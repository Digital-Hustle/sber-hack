--liquibase formatted sql

--changeset miroslavrov:20261009100300-doc-requirements-data
-- требование: FR-10 (набор документов — данные, ADR-010; состав — docs/DOC-TYPES.md)
insert into doc_requirements (doc_type, title, hint, condition, required, sort) values
('PASSPORT_RF', 'Паспорт гражданина РФ', 'Разворот с фото и страница с регистрацией', '{"citizenship": ["RF"]}', true, 10),
('SNILS', 'СНИЛС', 'Страховое свидетельство или уведомление АДИ-РЕГ', '{"citizenship": ["RF", "EAEU"]}', true, 60),
('INN', 'ИНН', 'Свидетельство о постановке на учёт', '{"citizenship": ["RF"]}', true, 70),
('MEDICAL', 'Медицинское заключение', 'Нужно, если должность требует медосмотра', '{"citizenship": ["RF"], "medicalRequired": true}', true, 80),
('PASSPORT_FOREIGN', 'Паспорт иностранного гражданина', 'Страница с фото и машиночитаемой строкой целиком', '{"citizenship": ["EAEU", "VISA_FREE"]}', true, 10),
('MIGRATION_CARD', 'Миграционная карта', 'Обе стороны, цель визита «работа»', '{"citizenship": ["EAEU", "VISA_FREE"]}', true, 20),
('ARRIVAL_NOTICE', 'Уведомление о прибытии', 'Отрывная часть бланка с отметкой', '{"citizenship": ["EAEU", "VISA_FREE"]}', true, 30),
('INN', 'ИНН', 'Если ИНН уже есть', '{"citizenship": ["EAEU", "VISA_FREE"], "hasInn": true}', true, 70),
('WORK_PATENT', 'Патент на работу', 'Лицевая сторона патента', '{"citizenship": ["VISA_FREE"]}', true, 40),
('MEDICAL', 'Медицинское заключение', 'Заключение медосвидетельствования или полис ДМС', '{"citizenship": ["VISA_FREE"]}', true, 50),
('SNILS', 'СНИЛС', 'Если СНИЛС уже есть', '{"citizenship": ["VISA_FREE"], "hasSnils": true}', true, 60);
--rollback delete from doc_requirements;
