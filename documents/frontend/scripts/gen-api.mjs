#!/usr/bin/env node
// Типы API из контрактов сервисов (contracts/openapi/*.json → src/shared/api/schema/<svc>.ts). В git не коммитим.
import { readdirSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, basename } from "node:path";
import openapiTS, { astToString } from "openapi-typescript";

const dir = resolve(import.meta.dirname, "../../contracts/openapi");
const out = resolve(import.meta.dirname, "../src/shared/api/schema");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(dir).filter((x) => x.endsWith(".json"))) {
  const ast = await openapiTS(new URL(`file://${resolve(dir, f)}`));
  writeFileSync(resolve(out, `${basename(f, ".json")}.ts`), "// сгенерировано npm run gen:api — не править\n" + astToString(ast));
  console.log(`✔ ${f}`);
}
