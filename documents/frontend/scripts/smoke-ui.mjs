// Ручной смоук UI (не в CI): vite preview --port 4173, затем CHROME=$(which chromium) node scripts/smoke-ui.mjs → /tmp/e2e-*.png.
// Ответы API подставляет page.route прямо здесь — в сборку приложения не попадает.
import { chromium } from "playwright";
const exe = process.env.CHROME ?? "chromium";
const B = "http://localhost:4173";
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const jwt = (p) => `${b64({ alg: "none" })}.${b64(p)}.x`;
const now = Math.floor(Date.now() / 1000);
const A = "11111111-1111-1111-1111-111111111111", D1 = "22222222-2222-2222-2222-222222222222", D2 = "33333333-3333-3333-3333-333333333333", F1 = "44444444-4444-4444-4444-444444444444";
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");
const card = {
  application: { id: A, applicantName: "Иванов Иван Иванович", status: "IN_REVIEW", assignee: "operator", submittedAt: new Date(Date.now() - 3600e3).toISOString(), missing: ["SNILS"] },
  counters: { documentsConfirmed: 1, documentsTotal: 2, fieldsAttention: 1, fieldsCorrected: 1, fieldsTotal: 9 },
  findings: [{ code: "CROSS_MISMATCH", field: "fullName", documentIds: [D1, D2], params: { field: "ФИО", a: "Паспорт РФ", b: "ИНН" } }, { code: "MISSING_DOCUMENT", documentIds: [], params: { type: "СНИЛС" } }],
  documents: [
    { id: D1, slotType: "PASSPORT_RF", type: "PASSPORT_RF", typeName: "Паспорт РФ", typeConfidence: 0.97, stage: "NEEDS_REVIEW", attention: true, confirmed: false, expiresOn: "2030-01-01",
      files: [{ id: F1, version: 1, active: true, mime: "application/pdf", pages: 2 }], pages: [{ n: 1, width: 1000, height: 1400 }, { n: 2, width: 1000, height: 1400 }], model: "qwen2.5", promptVersion: "v3",
      fields: [
        { name: "number", title: "Серия и номер", value: "45 ** ***123", masked: true, confidence: 0.55, attention: true, page: 1, bbox: [100, 200, 400, 260], checks: [{ code: "FORMAT", ok: true }] },
        { name: "fullName", title: "ФИО", value: "Иванов Иван Иванович", modelValue: "Иванов Иван Ивановч", corrected: true, correctedBy: "operator", confidence: 0.9, page: 1, bbox: [100, 300, 700, 360], checks: [] },
        { name: "birthDate", title: "Дата рождения", value: "1990-01-01", confidence: 0.95, page: 2, bbox: [100, 100, 300, 140], checks: [{ code: "NOT_IN_FUTURE", ok: true }, { code: "AGE_AT_ISSUE", ok: false }] },
        { name: "issuer", title: "Кем выдан", value: null, reason: "Нет в тексте", confidence: 0 },
      ] },
    { id: D2, slotType: "INN", type: "INN", typeName: "ИНН", typeConfidence: 0.88, stage: "PARSED", confirmed: true, confirmedBy: "operator", files: [{ id: F1, pages: 1 }], pages: [{ n: 1, width: 10, height: 10 }], fields: [] },
  ],
};
const routes = {
  "GET /review/api/v1/applications": { items: [{ id: A, applicantName: "Иванов Иван Иванович", status: "SUBMITTED", documents: 2, confirmed: 1, attention: 1, processing: 1, missing: ["SNILS"], waitingSeconds: 5400, assignee: "operator" }], page: { next: null, total: 1 } },
  [`GET /review/api/v1/applications/${A}`]: card,
  [`GET /review/api/v1/applications/${A}/history`]: { items: [{ id: "h1", action: "FIELD_CORRECTED", actor: "operator", source: "OPERATOR", createdAt: new Date().toISOString(), details: { field: "fullName", title: "ФИО", from: "Иванов Иван Ивановч", to: "Иванов Иван Иванович" } }] },
  "GET /review/api/v1/metrics": { computedAt: new Date().toISOString(), applications: { SUBMITTED: 3, ACCEPTED: 1 }, documents: { PARSED: 5, NEEDS_REVIEW: 2 }, waiting: { count: 3, p50: 3600, p95: 7200 }, decision: { count: 1, p50: 86400, p95: 86400 }, fieldsTotal: 40, fieldsCorrected: 4, formulas: { waiting: "now - submitted_at для SUBMITTED/IN_REVIEW" } },
  "GET /review/api/v1/reports/expiring": { days: 30, items: [{ applicationId: A, documentId: D1, applicantName: "Иванов И.И.", type: "PATENT", typeName: "Патент", expiresOn: "2026-10-20", daysLeft: 11, status: "ACCEPTED" }] },
  "GET /review/api/v1/stream/ticket": { ticket: "t" },
  "GET /docproc/api/v1/types": { items: [{ code: "PASSPORT_RF", name: "Паспорт РФ" }, { code: "INN", name: "ИНН" }] },
  "GET /docproc/api/v1/model/status": { provider: "ollama", model: "qwen2.5", reachable: true },
  "GET /intake/api/v1/applicant/questionnaire": { questions: [{ id: "citizenship", text: "Какое у вас гражданство?", kind: "single", options: [{ value: "RF", label: "Россия" }, { value: "EAEU", label: "Страна ЕАЭС", hint: "Армения, Беларусь" }] }, { id: "medicalRequired", text: "Должность требует медосмотра?", kind: "bool", options: [], showWhen: { citizenship: ["RF"] } }] },
  [`GET /intake/api/v1/applicant/applications/${A}`]: { id: A, status: "DRAFT", createdAt: new Date().toISOString(), profile: { citizenship: "RF" }, missing: ["SNILS"],
    requiredDocuments: [{ type: "PASSPORT_RF", title: "Паспорт РФ", hint: "Разворот с фото и регистрация", required: true, state: "DONE" }, { type: "SNILS", title: "СНИЛС", required: true, state: "PROBLEM" }, { type: "INN", title: "ИНН", required: false, state: "MISSING" }],
    documents: [{ id: D1, slotType: "INN", detectedType: "PASSPORT_RF", stage: "PARSED", reasonCode: "TYPE_MOVED", files: [{ id: F1, active: true, pages: 2 }] }, { id: D2, slotType: "SNILS", stage: "UNREADABLE", reasonCode: "IMAGE_BLURRY", files: [{ id: F1, pages: 1 }] }],
    requests: [{ id: "r1", type: "SNILS", reason: "Переснимите СНИЛС без бликов", resolved: false }] },
  [`GET /intake/api/v1/applicant/applications/${A}/stream/ticket`]: { ticket: "t" },
};
const br = await chromium.launch({ executablePath: exe, args: ["--no-sandbox"] });
const ctx = await br.newContext();
await ctx.route("**/*", async (route) => {
  const u = new URL(route.request().url());
  const m = route.request().method();
  if (u.pathname.endsWith("/stream")) return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": ok\n\n" });
  if (u.pathname.includes("/files/")) return route.fulfill({ status: 200, contentType: "image/png", body: png });
  if (u.pathname === "/auth/realms/documents/.well-known/openid-configuration")
    return route.fulfill({ json: { issuer: `${B}/auth/realms/documents`, authorization_endpoint: `${B}/auth/authorize`, token_endpoint: `${B}/auth/token`, end_session_endpoint: `${B}/auth/logout`, jwks_uri: `${B}/auth/jwks` } });
  if (u.pathname === "/auth/authorize") return route.fulfill({ status: 302, headers: { location: `${B}/operator/callback?code=c&state=${u.searchParams.get("state")}` } });
  if (u.pathname === "/auth/token") {
    const p = { iss: `${B}/auth/realms/documents`, aud: "documents-operator", sub: "u1", exp: now + 3600, iat: now, preferred_username: "operator", name: "Оператор Ольга", realm_access: { roles: ["OPERATOR", "SENIOR_OPERATOR"] } };
    return route.fulfill({ json: { access_token: jwt(p), id_token: jwt(p), token_type: "Bearer", expires_in: 3600, scope: "openid profile email" } });
  }
  const k = `${m} ${u.pathname}`;
  if (routes[k]) return route.fulfill({ json: routes[k] });
  if (u.pathname.startsWith("/intake") || u.pathname.startsWith("/review") || u.pathname.startsWith("/docproc")) return route.fulfill({ status: 204 });
  return route.continue();
});
const shot = async (pg, n) => { await pg.waitForTimeout(1200); await pg.screenshot({ path: `/tmp/e2e-${n}.png`, fullPage: true }); };
const errs = [];
const pg = await ctx.newPage();
pg.on("pageerror", (e) => errs.push(e.message));
pg.on("console", (m) => m.type() === "error" && errs.push("console: " + m.text()));
await pg.setViewportSize({ width: 390, height: 844 });
await pg.goto(`${B}/apply/${A}#k=secret`);
await shot(pg, "apply-app");
console.log("hash after:", new URL(pg.url()).hash);
await pg.setViewportSize({ width: 1440, height: 900 });
await pg.goto(`${B}/operator`);
await pg.getByRole("button", { name: "Войти" }).click();
await pg.waitForURL(`${B}/operator`);
await shot(pg, "queue");
await pg.getByText("Иванов Иван Иванович").first().click();
await pg.waitForTimeout(800);
await pg.getByText("Серия и номер").click();
await shot(pg, "card");
await pg.getByRole("tab", { name: "История" }).click();
await shot(pg, "history");
await pg.getByRole("link", { name: "Метрики" }).first().click();
await shot(pg, "metrics");
await pg.getByRole("link", { name: "Истекающие" }).first().click();
await shot(pg, "expiring");
console.log("ERRORS:", errs.join("\n"));
await br.close();
process.exit(0);
