/** Настройки окружения: всё относительно origin — один домен за Caddy (кабинет, API, Keycloak). */
export const env = {
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? `${location.origin}/auth/realms/documents`,
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? "documents-operator",
  uploadMaxMb: Number(import.meta.env.VITE_UPLOAD_MAX_MB ?? 20),
} as const;
