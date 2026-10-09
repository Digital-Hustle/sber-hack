# syntax=docker/dockerfile:1
# Контекст сборки — proxodnaya/frontend (CD: cd proxodnaya/frontend && docker build .).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . ./
RUN npm run build

# Статика SPA за внешним Caddy (infra/Caddyfile → frontend:80). Любой неизвестный путь — index.html (BrowserRouter).
FROM caddy:2-alpine
COPY --from=build /app/dist /srv
COPY Caddyfile.spa /etc/caddy/Caddyfile
EXPOSE 80
