/** Настройки окружения: Keycloak за тем же хостом (Caddy /auth), клиент subsidy-specialist (PKCE). */
export const env = {
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? `${location.origin}/auth/realms/subsidy`,
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? "subsidy-specialist",
} as const;
