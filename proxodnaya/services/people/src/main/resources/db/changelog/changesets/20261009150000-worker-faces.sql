--liquibase formatted sql

--changeset b1:20261009150000-worker-faces
-- эталон лица живёт в face (зашифрованный эмбеддинг); здесь только факт и качество для карточки
create table worker_faces (
    worker_id   uuid primary key references workers (id),
    templates   int         not null check (templates between 1 and 10),
    quality     real        not null check (quality between 0 and 1),
    enrolled_at timestamptz not null,
    enrolled_by text        not null
);
--rollback drop table worker_faces;
