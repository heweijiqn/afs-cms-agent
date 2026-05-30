import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: path.resolve(__dirname, "../src/main/resources/static"),
    emptyOutDir: false,
    assetsDir: "console",
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        entryFileNames: "console/app-[hash].js",
        chunkFileNames: "console/[name]-[hash].js",
        assetFileNames: assetInfo => {
          if (assetInfo.name && assetInfo.name.endsWith(".css")) {
            return "console/console-[hash].css";
          }
          return "console/[name]-[hash][extname]";
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/cms": "http://127.0.0.1:9901",
      "/api": "http://127.0.0.1:9901",
      "/open": "http://127.0.0.1:9901",
      "/health": "http://127.0.0.1:9901",
      "/healthX": "http://127.0.0.1:9901",
      "/daily": "http://127.0.0.1:9901",
      "/media": "http://127.0.0.1:9901"
    }
  }
});
