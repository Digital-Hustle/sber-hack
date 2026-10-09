import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { MotionConfig } from "motion/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster, applyStoredTheme } from "@/shared/ui";
import { router } from "./providers/router";
import { queryClient } from "./providers/query";
import "./styles/index.css";

// Тема — до первой отрисовки (inline-скрипт в index.html запрещён CSP script-src 'self').
applyStoredTheme();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <RouterProvider router={router} />
        <Toaster />
      </MotionConfig>
    </QueryClientProvider>
  </StrictMode>,
);
