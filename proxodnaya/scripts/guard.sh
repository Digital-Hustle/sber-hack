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
g "(decision|result)\s*[:=]\s*['\"]ALLOW['\"]|setDecision\(\s*['\"]ALLOW" -- 'frontend/apps/**' ':!*.test.*' ':!*.spec.*' \
  && F "фронтенд сам выставляет ALLOW — решение принимает только сервер"

# 3. Хранилище только в памяти / в файле
g 'h2database|sqlite|hsqldb' -- 'backend/pom.xml' && F "встраиваемая БД в зависимостях — данные только в PostgreSQL"
g 'static\s+(final\s+)?(Map|List|Set)<' -- 'backend/src/main/**' ':!**/common/**' && W "статическая коллекция в main-коде — не используется ли как хранилище?"
g 'new (FileWriter|FileOutputStream)\(|Files\.write' -- 'backend/src/main/**' ':!**/reports/**' && W "запись в файл — данные должны быть в БД"

# 4. Заглушки модели и аналитики
g -i '(mock|stub|fake|canned|hardcoded)' -- 'backend/src/main/**/llm/**' 'backend/src/main/**/assistant/**' 'backend/src/main/**/analytics/**' \
  && F "mock/stub/fake в боевом коде LLM/помощника/аналитики"
g "return\s+\"[^\"]{40,}\"" -- 'backend/src/main/**/assistant/**' 'backend/src/main/**/llm/**' && W "длинная строковая константа в ответе помощника — не заготовка ли?"
g '(const|let)\s+\w*(mock|fake|demo)\w*\s*=\s*\[' -- 'frontend/apps/**' ':!*.test.*' ':!*.stories.*' && F "моковые данные во фронтенде"

# 5. Обход проверок
g '@Disabled|\.skip\(|xit\(' -- 'backend/src/test/**' 'frontend/**/*.test.*' && W "отключённые тесты"
g 'continue-on-error:\s*true' -- "$(git rev-parse --show-toplevel)/.github" && F "continue-on-error в CI"
g 'permitAll\(\)' -- 'backend/src/main/**' && W "permitAll — убедись, что это только health/openid"

# 6. Токены в localStorage, время без Clock
g -i 'localStorage\.setItem\([^)]*(token|jwt)' -- 'frontend/**' && F "токены в localStorage"
g 'Instant\.now\(\)|LocalDateTime\.now\(\)' -- 'backend/src/main/**' && W "время без Clock — используй внедрённый Clock"

# 7. Правка смерженных миграций
if git rev-parse --verify -q origin/main >/dev/null; then
  git diff --name-status origin/main...HEAD -- 'backend/src/main/resources/db/migration/' | grep -E '^(M|D|R)' \
    && F "изменена/удалена существующая миграция — создай новую"
fi

[ $fail -eq 0 ] && { [ $warn -eq 0 ] && echo "✔ guard: чисто" || echo "✔ guard: FAIL нет, проверь WARN"; }
exit $fail
