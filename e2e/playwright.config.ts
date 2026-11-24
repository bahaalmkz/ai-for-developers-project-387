import { defineConfig } from '@playwright/test'

// Порты отличаются от dev-режима (8080 и 5173), чтобы проверка не мешала запущенным демонам
const backendPort = 18080
const frontendPort = 15173

// Maven: в mise это `mvn`, в scripts/check.ps1 подставляется mvnw.cmd (команда запускается из каталога backend)
const mvn = process.env.E2E_MVN ?? 'mvn'

export default defineConfig({
  testDir: './tests',
  // Один сценарий на одной общей БД: параллельность только мешает
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${frontendPort}`,
    // E2E_TRACE=on в браузерной проверке PR: трассировка и скриншоты для любого исхода
    trace: process.env.E2E_TRACE === 'on' ? 'on' : 'retain-on-failure',
    screenshot: process.env.E2E_TRACE === 'on' ? 'on' : 'only-on-failure',
  },
  webServer: [
    {
      // Настоящий бэкенд без профиля dev (нет демо-данных) на чистой БД в памяти
      command: `${mvn} -B -q spring-boot:run "-Dspring-boot.run.arguments=--server.port=${backendPort} --spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1"`,
      cwd: '../backend',
      url: `http://localhost:${backendPort}/api/health`,
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: `npm run dev -- --port ${frontendPort} --strictPort`,
      cwd: '../frontend',
      env: { API_PROXY_TARGET: `http://localhost:${backendPort}` },
      url: `http://localhost:${frontendPort}`,
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
