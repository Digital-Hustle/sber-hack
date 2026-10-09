import { FolderOpen } from "lucide-react";
import { PageHeader, EmptyState } from "@/shared/ui";

/** Очередь пакетов (FR-61). Данные появятся вместе с сервисом package — пока честное пустое состояние. */
export const PackagesPage = () => (
  <div className="flex flex-col gap-6">
    <PageHeader title="Пакеты" sub="Пакеты документов организаций на проверку" />
    <EmptyState icon={<FolderOpen />} title="Пакетов пока нет" text="Создайте пакет и загрузите документы организации" className="py-24" />
  </div>
);
