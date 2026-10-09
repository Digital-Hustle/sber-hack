import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";

// Одна сборка на обе зоны (/apply, /operator); API и Keycloak — тот же origin через Caddy.
// В dev запросы к API проксируются на стенд: VITE_PROXY=https://localhost npm run dev
const target = process.env.VITE_PROXY ?? "https://localhost";
const proxy = Object.fromEntries(["/intake", "/review", "/docproc", "/auth"].map((p) => [p, { target, changeOrigin: true, secure: false }]));

export default defineConfig({
  base: "/",
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: { host: true, proxy },
  build: { chunkSizeWarningLimit: 1500 },
});
