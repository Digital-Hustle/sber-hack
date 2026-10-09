# syntax=docker/dockerfile:1
# Сборка из compose: build: { context: .., dockerfile: frontend/Dockerfile } — контекст = documents/.
FROM node:22-alpine AS build
WORKDIR /app
COPY contracts/ ./contracts/
COPY frontend/package.json frontend/package-lock.json ./frontend/
WORKDIR /app/frontend
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# режим стенда: dev — тестовый вход в сборке, prod — боевая сборка (ADR-021)
ARG VITE_APP_MODE=prod
ENV VITE_APP_MODE=$VITE_APP_MODE
RUN npm run gen:api && npm run build

# Статика + reverse proxy. Caddyfile подключается томом из infra/caddy/Caddyfile.
FROM caddy:2-alpine
COPY --from=build /app/frontend/dist /srv
EXPOSE 80 443
