#!/usr/bin/env bash
# Боевой деплой «Проходной» на общий сервер без GHCR: jar-ы → образы локально → compose up.
#   ssh srv 'cd /opt/gate && git fetch -q origin && git checkout -q -f origin/main && proxodnaya/scripts/deploy-server.sh'
# Нужны: Java 21, Docker, infra/.env (scripts/gen-env.sh + правки из docs/DEPLOY.md «Стенд за edge»).
# jOOQ генерируется из живой БД — поднимаем временный postgres на 127.0.0.1:5439, чтобы не мешать серверному CI (ci-pg:5432).
set -euo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export DB_HOST=127.0.0.1 DB_PORT=5439 DB_ADMIN_USER=postgres DB_ADMIN_PASSWORD=postgres
PG=gate-build-pg
cleanup() { docker rm -f $PG >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup
docker run -d --name $PG -p 127.0.0.1:5439:5432 -e POSTGRES_PASSWORD=postgres postgres:16-alpine >/dev/null
until docker exec $PG pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
cd "$CASE/services"
for d in */src/main/resources/db/changelog; do svc=${d%%/*}
  docker exec $PG psql -qU postgres -c "create database $svc" >/dev/null
  ./gradlew --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME :$svc:update :$svc:generateJooq
done
./gradlew --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME bootJar -x test
cd "$CASE/infra"
docker compose -p proxodnaya build
docker compose -p proxodnaya up -d --wait --wait-timeout 900
docker image prune -f >/dev/null
echo "✔ стенд поднят: https://$(grep -E '^PUBLIC_HOST=' .env | cut -d= -f2)"
