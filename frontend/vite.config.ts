import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Запросы к API в режиме разработки уходят на бэкенд Spring Boot
      // Адрес можно подменить (браузерный сценарий поднимает бэкенд на другом порту)
      '/api': process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    // Глобальные afterEach нужны Testing Library для автоочистки DOM между тестами
    globals: true,
  },
})
