import { createBrowserRouter, Navigate } from "react-router";
import { Aurora } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { SpecialistShell } from "@/widgets/specialist-shell";
import { PackagesPage } from "@/pages/packages";

const RootLayout = ({ children }: { children: React.ReactNode }) => (
  <div className="relative isolate min-h-svh text-foreground">
    <Aurora fixed />
    {children}
  </div>
);

// Зона специалиста /specialist (rules.md §4). Разделы добавляются по мере готовности серверной части.
export const router = createBrowserRouter([
  {
    path: routes.specialist,
    element: <RootLayout><SpecialistShell /></RootLayout>,
    children: [
      { index: true, element: <Navigate to={routes.packages} replace /> },
      { path: "packages", element: <PackagesPage /> },
    ],
  },
  { path: "*", element: <Navigate to={routes.packages} replace /> },
]);
