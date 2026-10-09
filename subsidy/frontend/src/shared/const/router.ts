export const routes = {
  home: "/",
  specialist: "/specialist",
  callback: "/specialist/callback",
  packages: "/specialist/packages",
  pkg: (id: string) => `/specialist/packages/${id}`,
  stats: "/specialist/stats",
  expiring: "/specialist/expiring",
  assistant: "/specialist/assistant",
  programs: "/specialist/programs",
  org: (inn: string) => `/specialist/organizations/${inn}`,
} as const;
