export const routes = {
  home: "/",
  specialist: "/specialist",
  packages: "/specialist/packages",
  pkg: (id: string) => `/specialist/packages/${id}`,
  stats: "/specialist/stats",
  expiring: "/specialist/expiring",
  assistant: "/specialist/assistant",
} as const;
