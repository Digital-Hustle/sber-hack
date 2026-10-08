#!/usr/bin/env node
// Проверка архитектуры Feature-Sliced Design (эталон: MelevaTe/hackathon-spring-2026-frontend).
// Плюс: анимации только motion/react (banned-import, css-animation — ADR-031).
// Использование: node fsd-check.mjs [путь/к/src] [--warn-only]
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve, dirname, sep } from "node:path";

const args = process.argv.slice(2);
const warnOnly = args.includes("--warn-only");
const SRC = resolve(args.find((a) => !a.startsWith("--")) ?? "src");
const LAYERS = ["shared", "entities", "features", "widgets", "pages", "app"]; // снизу вверх
const SLICED = new Set(["entities", "features", "widgets", "pages"]);
const rank = (l) => LAYERS.indexOf(l);

const files = [];
(function walk(d) {
  for (const n of readdirSync(d)) {
    const p = join(d, n);
    if (statSync(p).isDirectory()) walk(p);
    else if (/\.(ts|tsx|css)$/.test(n) && !/\.d\.ts$/.test(n)) files.push(p);
  }
})(SRC);

// src/<layer>/<slice>/<segment>/...
function locate(absPath) {
  const parts = relative(SRC, absPath).split(sep);
  const layer = parts[0];
  if (!LAYERS.includes(layer)) return { layer: null, parts };
  const slice = SLICED.has(layer) ? parts[1] : null;
  return { layer, slice, parts };
}

const problems = [];
const add = (file, line, rule, msg) =>
  problems.push(`${relative(process.cwd(), file)}:${line}  [${rule}] ${msg}`);

// 0. Анимации только motion/react (ADR-031); WebGL — только по ADR
const BANNED = [
  [/^(gsap|@gsap\/.*)$/, "GSAP запрещён — анимации только motion/react (ADR-031)"],
  [/^(lenis|@studio-freight\/lenis)$/, "Lenis запрещён — прокрутка через useScroll из motion/react (ADR-031)"],
  [/^(react-spring|@react-spring\/.*)$/, "react-spring запрещён — используйте motion/react (ADR-031)"],
  [/^(animejs|popmotion)$/, "сторонний движок анимаций запрещён — используйте motion/react (ADR-031)"],
  [/^framer-motion(\/.*)?$/, "импортируйте из \"motion/react\", а не из framer-motion (ADR-031)"],
  [/^(tw-animate-css|tailwindcss-animate)$/, "CSS-анимации запрещены — перепишите на motion/react (ADR-031)"],
  [/^(three|ogl|postprocessing|@react-three\/.*)(\/.*)?$/, "WebGL-зависимость без ADR — уберите или оформите ADR (ADR-031)"],
];
const CSS_ANIM_RE = /@keyframes\s|(?<![\w-])(?:[\w-]+:)*animate-(?:spin|ping|pulse|bounce|in|out|\[)/;
const IMPORT_RE = /(?:import|export)\s[^"']*?from\s*["']([^"']+)["']|import\(\s*["']([^"']+)["']\s*\)|^\s*import\s*["']([^"']+)["']|@import\s+["']([^"']+)["']/g;

for (const file of files) {
  const src = readFileSync(file, "utf8");
  const me = locate(file);
  if (!me.layer) continue;
  const lines = src.split("\n");
  lines.forEach((text, i) => {
    if (CSS_ANIM_RE.test(text))
      add(file, i + 1, "css-animation", "CSS-анимация (@keyframes / animate-*) — перепишите на motion/react (ADR-031)");
    for (const m of text.matchAll(IMPORT_RE)) {
      const spec = m[1] ?? m[2] ?? m[3] ?? m[4];
      let targetAbs;
      const viaAlias = spec.startsWith("@/");
      if (viaAlias) targetAbs = join(SRC, spec.slice(2));
      else if (spec.startsWith(".")) targetAbs = resolve(dirname(file), spec);
      else {
        for (const [re, msg] of BANNED) if (re.test(spec)) add(file, i + 1, "banned-import", `"${spec}": ${msg}`);
        continue; // npm-пакет
      }
      const t = locate(targetAbs);
      if (!t.layer) continue;
      const ln = i + 1;
      const sameSlice = me.layer === t.layer && (me.slice === t.slice || !SLICED.has(me.layer));

      // 1. Направление зависимостей: только вниз
      if (rank(t.layer) > rank(me.layer))
        add(file, ln, "layer-direction", `${me.layer} не может импортировать ${t.layer} ("${spec}")`);

      // 2. Слайсы одного слоя не знают друг о друге
      if (SLICED.has(me.layer) && me.layer === t.layer && me.slice !== t.slice)
        add(file, ln, "cross-slice", `${me.layer}/${me.slice} импортирует соседний ${t.layer}/${t.slice} — поднимите композицию на слой выше`);

      // 3. Чужой слайс — только через публичный API (index.ts)
      if (SLICED.has(t.layer) && !sameSlice) {
        const depth = t.parts.length - 2; // всё после layer/slice
        const isIndex = depth <= 0 || (depth === 1 && /^index(\.tsx?)?$/.test(t.parts[2]));
        if (!isIndex) add(file, ln, "public-api", `глубокий импорт "${spec}" — импортируйте из "@/${t.layer}/${t.slice}"`);
      }

      // 4. Внутри своего слайса — относительные пути
      if (viaAlias && sameSlice && SLICED.has(me.layer))
        add(file, ln, "relative-inside-slice", `внутри ${me.layer}/${me.slice} используйте относительный импорт вместо "${spec}"`);
    }
  });
}

// 5. У каждого слайса есть index.ts
for (const layer of SLICED) {
  let slices = [];
  try { slices = readdirSync(join(SRC, layer)); } catch { continue; }
  for (const s of slices) {
    const dir = join(SRC, layer, s);
    if (!statSync(dir).isDirectory()) continue;
    const has = readdirSync(dir).some((n) => /^index\.tsx?$/.test(n));
    if (!has) add(dir, 0, "no-public-api", `у слайса ${layer}/${s} нет index.ts`);
  }
}

if (problems.length === 0) {
  console.log(`✔ fsd-check: ${files.length} файлов, нарушений нет`);
  process.exit(0);
}
console.log(problems.join("\n"));
console.log(`\n${warnOnly ? "⚠" : "✖"} fsd-check: ${problems.length} нарушений в ${files.length} файлах`);
process.exit(warnOnly ? 0 : 1);
