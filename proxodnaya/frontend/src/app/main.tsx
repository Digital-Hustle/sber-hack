import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { MotionConfig } from "motion/react";
import { Toaster, applyStoredTheme } from "@/shared/ui";
import { setupPwa } from "@/shared/lib";
import { router, prefetchRoutes } from "./providers/router";
import "./styles/index.css";

applyStoredTheme();
setupPwa();
prefetchRoutes();

// Заставка из index.html: плавно гасим после первой отрисовки приложения
requestAnimationFrame(() => requestAnimationFrame(() => {
  const s = document.getElementById("splash");
  if (!s) return;
  s.classList.add("out");
  s.addEventListener("transitionend", () => s.remove(), { once: true });
}));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <RouterProvider router={router} />
      <Toaster />
    </MotionConfig>
  </StrictMode>,
);
