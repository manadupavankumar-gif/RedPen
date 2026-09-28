import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The proxy sends any request starting with /api to the Spring Boot backend (port 8080).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
