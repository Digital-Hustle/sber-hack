// Плагин сборки: собирает service worker со списком всех файлов сборки (ADR-043). Без внешних зависимостей.
import { createHash } from "node:crypto";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import type { Plugin } from "vite";

const walk = (dir: string): string[] => readdirSync(dir).flatMap((f) => {
  const p = join(dir, f);
  return statSync(p).isDirectory() ? walk(p) : [p];
});

export const pwa = (): Plugin => ({
  name: "proxodnaya-pwa",
  apply: "build",
  generateBundle(_, bundle) {
    const pub = join(process.cwd(), "public");
    const files = [...Object.keys(bundle), ...walk(pub).map((p) => relative(pub, p).split("\\").join("/"))]
      .filter((f) => !f.endsWith(".map") && f !== "sw.js");
    const list = ["/", ...files.map((f) => `/${f}`)];
    const version = createHash("sha256").update(list.join("\n")).digest("hex").slice(0, 12);
    const src = readFileSync(join(process.cwd(), "scripts/sw.js"), "utf8")
      .replace(`"__VERSION__"`, JSON.stringify(version))
      .replace("= __PRECACHE__;", `= ${JSON.stringify(list)};`);
    this.emitFile({ type: "asset", fileName: "sw.js", source: src });
  },
});
