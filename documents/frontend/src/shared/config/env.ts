/** Настройки окружения: всё относительно origin — один домен за Caddy (кабинет, API, Keycloak). */
export const env = {
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? `${location.origin}/auth/realms/documents`,
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? "documents-operator",
  uploadMaxMb: Number(import.meta.env.VITE_UPLOAD_MAX_MB ?? 20),
  /** APP_MODE=dev (ADR-021): тестовый стенд — быстрый вход и код 000000. В prod-сборке константа false, код вырезается. */
  testMode: import.meta.env.VITE_APP_MODE === "dev",
} as const;
