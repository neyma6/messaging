import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      '/api': {
        target: 'http://api-gateway:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // Also proxy ws for messaging?
      // No, messaging-service registers 'ws://localhost:8085/ws'. Client connects directly.
      // But inside Docker (if browser is outside), 'localhost' refers to Host machine.
      // If client is in browser, it connects to 'localhost:8085'.
      // Docker maps 8085:8080 (messaging-service).
      // So this works WITHOUT proxy.
    }
  }
})
