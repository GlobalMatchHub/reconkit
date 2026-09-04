import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The API is served by Spring Boot on 8080. Proxying rather than pointing the
    // browser straight at it keeps the app on one origin in development, which is the
    // same shape it has in production where both are served from the same jar.
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  build: {
    // The built assets are served by the Spring Boot application.
    outDir: '../recon-app/src/main/resources/static',
    emptyOutDir: true,
  },
})
