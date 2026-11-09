import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Todo lo que empiece con /api va al backend de Spring Boot
      '/api': 'http://localhost:8081',
    },
  },
})
