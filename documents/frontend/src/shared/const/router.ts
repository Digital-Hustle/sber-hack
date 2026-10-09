export const routes = {
  home: "/",
  apply: "/apply",
  applyApplication: (id: string) => `/apply/${id}`,
  applyResume: "/apply/resume",
  operator: "/operator",
  operatorApplication: (id: string) => `/operator/applications/${id}`,
  operatorExpiring: "/operator/expiring",
  operatorMetrics: "/operator/metrics",
  operatorStaff: "/operator/staff",
} as const;
