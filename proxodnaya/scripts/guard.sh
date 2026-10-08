#!/usr/bin/env bash
# Сканер «красных линий» из rules.md §1. FAIL = нельзя открывать PR. WARN = посмотри глазами.
set -uo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
fail=0; warn=0
F() { echo "✖ FAIL: $*"; fail=1; }
W() { echo "⚠ WARN: $*"; warn=1; }
g() { git grep -nIE "$@" 2>/dev/null; }   # только отслеживаемые и проиндексированные файлы

# 1. Секреты
git ls-files "$(git rev-parse --show-toplevel)" | grep -E '(^|/)\.env$|\.pem$|id_rsa|\.p12$|\.jks$' && F "в git лежат файлы секретов (.env/ключи)"
if command -v gitleaks >/dev/null; then
  gitleaks detect --no-banner --redact -v >/dev/null 2>&1 || F "gitleaks нашёл секреты: запусти 'gitleaks detect -v'"
else
  g '(api[_-]?key|secret|password|token)\s*[:=]\s*["'"'"'][A-Za-z0-9/+_\-]{16,}' -- ':!*.md' ':!*.example' ':!*test*' && W "похоже на захардкоженный секрет (установи gitleaks для точной проверки)"
fi

# 2. Решение о проходе на клиенте
g "(decision|result)\s*[:=]\s*['\"]ALLOW['\"]|setDecision\(\s*['\"]ALLOW" -- 'frontend/src/**' ':!*.test.*' ':!*.spec.*' ':!**/mocks/**' \
  && F "фронтенд сам выставляет ALLOW — решение принимает только сервер"

# 3. Хранилище только в памяти / в файле
g 'h2database|sqlite|hsqldb' -- 'services/**/build.gradle.kts' 'services/gradle/libs.versions.toml' 'services/*/pyproject.toml' && F "встраиваемая БД в зависимостях — данные только в PostgreSQL"
g 'static\s+(final\s+)?(Map|List|Set)<' -- 'services/*/src/main/**' ':!services/libs/**' && W "статическая коллекция в main-коде — не используется ли как хранилище?"
g 'new (FileWriter|FileOutputStream)\(|Files\.write' -- 'services/*/src/main/**' ':!services/shift/**/reports/**' && W "запись в файл — данные должны быть в БД"

# 4. Заглушки модели и аналитики
g -i '(mock|stub|fake|canned|hardcoded)' -- 'services/assistant/src/main/**' 'services/shift/src/main/**/analytics/**' \
  && F "mock/stub/fake в боевом коде LLM/помощника/аналитики"
g "return\s+\"[^\"]{40,}\"" -- 'services/assistant/src/main/**' && W "длинная строковая константа в ответе помощника — не заготовка ли?"
g '(const|let)\s+\w*(mock|fake|demo)\w*\s*=\s*\[' -- 'frontend/src/**' ':!*.test.*' ':!*.stories.*' ':!**/mocks/**' && F "моковые данные во фронтенде"
g 'from ["'"'"']msw' -- 'frontend/src/**' ':!**/mocks/**' ':!src/main.tsx' && F "MSW вне src/mocks — моки только в dev"
grep -qs 'USE_MOCKS' frontend/src/main.tsx && ! grep -qs 'import.meta.env.DEV' frontend/src/main.tsx && F "моки включаются не только в dev"

# 5. Обход проверок
g '@Disabled|\.skip\(|xit\(' -- 'services/*/src/test/**' 'frontend/**/*.test.*' && W "отключённые тесты"
g 'continue-on-error:\s*true' -- "$(git rev-parse --show-toplevel)/.github" && F "continue-on-error в CI"
g 'permitAll\(\)' -- 'services/*/src/main/**' && W "permitAll — убедись, что это только health/openid"

# 6. Токены в localStorage, время без Clock
g -i 'localStorage\.setItem\([^)]*(token|jwt)' -- 'frontend/**' && F "токены в localStorage"
g 'Instant\.now\(\)|LocalDateTime\.now\(\)|OffsetDateTime\.now\(\)' -- 'services/*/src/main/**' && W "время без Clock — используй внедрённый Clock"
g '\bminio\b|qdrant' -- 'services/**/build.gradle.kts' 'services/gradle/libs.versions.toml' 'services/*/pyproject.toml' 'infra/docker-compose*.yml' && F "MinIO (AGPL) / лишнее хранилище — п. 5.8.4, фото и векторы в Postgres"

# 6.1 Микросервисные границы
g 'kafkaTemplate\.send\(' -- 'services/*/src/main/**' ':!services/libs/**' ':!**/messaging/outbox/**' && F "публикация события мимо outbox"
g 'X-User-Id|ThreadLocal<' -- 'services/*/src/main/**' && F "контекст пользователя из заголовка/ThreadLocal — только из JWT"
SVCS=$(for d in services/*/; do [ -f "$d/build.gradle.kts" ] || [ -f "$d/pyproject.toml" ] && basename "$d"; done 2>/dev/null | grep -vE '^(libs|build-logic|gradle|config)$')
for svc in $SVCS; do
  others=$(grep -vx "$svc" <<<"$SVCS" | paste -sd'|' -)
  [ -n "$others" ] && g "jdbc:postgresql://[^\"]*/($others)\b" -- "services/$svc/src/main/**" && F "$svc подключается к чужой БД"
done
[ -f infra/docker-compose.yml ] && awk '/^  [a-z-]+:$/{svc=$1} /^    ports:/{print svc}' infra/docker-compose.yml | grep -vE '^(caddy|ollama):$' && W "сервисы с проброшенными портами — наружу только caddy"
g 'org\.opencv' -- 'services/*/src/main/**' && F "OpenCV в Java — распознавание лиц только в Python-сервисе face"
g -i 'insightface|buffalo_l|antelopev2' -- 'services/face/**' && F "InsightFace: веса только для некоммерческого использования"

# 6.2 Структура (скиллы backend-spring / frontend-fsd)
REPO=$(git rev-parse --show-toplevel)
[ -f services/settings.gradle.kts ] && { node "$REPO/.agents/skills/backend-spring/scripts/be-check.mjs" services >/dev/null || F "be-check: нарушены правила backend-spring (запусти его без >/dev/null)"; }
[ -d frontend/src ] && { node "$REPO/.agents/skills/frontend-fsd/scripts/fsd-check.mjs" frontend/src >/dev/null || F "fsd-check: нарушены правила FSD (запусти его без >/dev/null)"; }

# 7. Правка смерженных миграций
if git rev-parse --verify -q origin/main >/dev/null; then
  git diff --name-status origin/main...HEAD -- 'services/*/src/main/resources/db/changelog/changesets/' 'services/*/db/changelog/changesets/' | grep -E '^(M|D|R)' \
    && F "изменена/удалена существующая миграция — создай новую"
fi

[ $fail -eq 0 ] && { [ $warn -eq 0 ] && echo "✔ guard: чисто" || echo "✔ guard: FAIL нет, проверь WARN"; }
exit $fail
