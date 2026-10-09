import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";
import { pwa } from "./scripts/vite-pwa";

// base "/" — SPA за gateway/nginx с fallback на index.html (BrowserRouter: ссылки-инвайты /worker/activate?c=…).
export default defineConfig({
  base: "/",
  plugins: [react(), tailwindcss(), pwa()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: { host: true },
  build: { chunkSizeWarningLimit: 1500 },
});
