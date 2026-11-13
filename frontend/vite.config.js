import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The Spring Boot backend (default http://localhost:8080). Override with BACKEND_URL.
const backend = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Same-origin proxy: the browser only talks to Vite, so no CORS setup is needed.
    proxy: {
      '/api': backend,
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true },
    },
  },
})
