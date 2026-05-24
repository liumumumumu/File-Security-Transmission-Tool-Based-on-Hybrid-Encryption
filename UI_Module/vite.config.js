import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:20201",
        changeOrigin: true,
      },
      "/incoming": {
        target: "http://127.0.0.1:20201",
        changeOrigin: true,
      },
      "/accept": {
        target: "http://127.0.0.1:20201",
        changeOrigin: true,
      },
      "/reject": {
        target: "http://127.0.0.1:20201",
        changeOrigin: true,
      },
      "/retransmit": {
        target: "http://127.0.0.1:20201",
        changeOrigin: true,
      },
    },
  },
});

