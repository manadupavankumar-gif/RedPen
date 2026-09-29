import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The proxy sends any request starting with /api to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "https://redpen-m3z4.onrender.com",
    },
  },
});
