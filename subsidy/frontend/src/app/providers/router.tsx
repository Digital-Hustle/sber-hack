import { createBrowserRouter, Navigate } from "react-router";
import { Aurora } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { SpecialistShell } from "@/widgets/specialist-shell";
import { PackagesPage } from "@/pages/packages";
import { CallbackPage } from "@/pages/callback";
import { PackagePage } from "@/pages/package";
import { ExpiringPage } from "@/pages/expiring";
import { StatsPage } from "@/pages/stats";
import { ProgramsPage } from "@/pages/programs";
import { OrganizationPage } from "@/pages/organization";

const RootLayout = ({ children }: { children: React.ReactNode }) => (
  <div className="relative isolate min-h-svh text-foreground">
    <Aurora fixed />
    {children}
  </div>
);

// Зона специалиста /specialist (rules.md §4). Разделы добавляются по мере готовности серверной части.
export const router = createBrowserRouter([
  { path: routes.callback, element: <RootLayout><CallbackPage /></RootLayout> },
  {
    path: routes.specialist,
    element: <RootLayout><SpecialistShell /></RootLayout>,
    children: [
      { index: true, element: <Navigate to={routes.packages} replace /> },
      { path: "packages", element: <PackagesPage /> },
      { path: "packages/:id", element: <PackagePage /> },
      { path: "expiring", element: <ExpiringPage /> },
      { path: "stats", element: <StatsPage /> },
      { path: "programs", element: <ProgramsPage /> },
      { path: "organizations/:inn", element: <OrganizationPage /> },
    ],
  },
  { path: "*", element: <Navigate to={routes.packages} replace /> },
]);
