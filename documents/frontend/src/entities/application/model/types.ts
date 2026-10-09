import type { IntakeSchema, ReviewSchema, DocprocSchema } from "@/shared/api";

export type ApplicantApplication = IntakeSchema["schemas"]["ApplicationView"];
export type ApplicantDocument = IntakeSchema["schemas"]["DocumentView"];
export type RequiredDocument = IntakeSchema["schemas"]["RequiredDocument"];
export type Question = IntakeSchema["schemas"]["Question"];
export type Created = IntakeSchema["schemas"]["Created"];
export type Uploaded = IntakeSchema["schemas"]["Uploaded"];
export type OperatorRequest = IntakeSchema["schemas"]["OperatorRequest"];

export type QueueItem = ReviewSchema["schemas"]["QueueItem"];
export type QueuePage = ReviewSchema["schemas"]["QueueRs"];
export type Card = ReviewSchema["schemas"]["CardRs"];
export type DocumentCard = ReviewSchema["schemas"]["DocumentCard"];
export type FieldCard = ReviewSchema["schemas"]["FieldCard"];
export type Finding = ReviewSchema["schemas"]["Finding"];
export type HistoryEntry = ReviewSchema["schemas"]["HistoryEntry"];
export type Metrics = ReviewSchema["schemas"]["MetricsRs"];
export type Expiring = ReviewSchema["schemas"]["ExpiringRs"];
export type ModelStatus = DocprocSchema["schemas"]["ModelStatusRs"];
export type DocType = DocprocSchema["schemas"]["Item"];

/** Страница документа в системе координат bbox. */
export type PageSize = { n: number; width: number; height: number };
/** Файл документа из intake. */
export type DocFile = { id: string; version?: number; active?: boolean; mime?: string; pages?: number };
export type Check = { code: string; ok: boolean };
