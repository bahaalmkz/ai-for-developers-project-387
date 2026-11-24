# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Команды запуска, проверок и правила коммитов — в [AGENTS.md](AGENTS.md). Здесь только то, что нужно понять, читая несколько файлов.

## Архитектура

Сервис бронирования времени (см. README.md: типы встреч, слоты, занятость). API: `GET /api/health`, типы встреч (`/api/event-types`), слоты, записи (`/api/bookings`). Данные в встроенной H2 (`docs/adr/0004`), расписание фиксировано (`docs/adr/0003`).

**Контракт — источник правды.** `contract/main.tsp` (TypeSpec) компилируется в `contract/openapi.yaml`. Оба файла коммитятся, и менять API нужно в TypeSpec, затем перекомпилировать.

**Бэкенд (`backend/`, Spring Boot).** Серверный интерфейс не пишется руками: плагин `openapi-generator-maven-plugin` в `pom.xml` генерирует `HealthApi` и модели из `contract/openapi.yaml` во время сборки (`target/generated-sources`). Контроллер (`health/HealthController`) реализует этот интерфейс. Значит, новый маршрут сначала появляется в контракте, затем в контроллере, и компилятор не даст их разойтись.

**Фронтенд (`frontend/`, React + Vite).** Клиент API генерируется из того же `openapi.yaml` генератором `typescript-fetch` в `src/api/generated` (папка в `.gitignore`, ее создает `npm run generate:api`). Поэтому `mise run check` и хук pre-commit сначала вызывают генерацию. В dev-режиме Vite проксирует `/api` на `localhost:8080`, а клиент использует относительный `basePath: ''` — иначе запросы пошли бы мимо прокси.

**Проверки — одна цепочка.** `mise run check` = контракт → генерация SDK → Spotless + oxlint (с `--deny-warnings`) + jactionlint для workflow-файлов → сборка фронтенда → тесты бэкенда и фронтенда (Vitest) → браузерный сценарий Playwright (`e2e`). Та же команда вызывается из `.github/workflows/ci.yml` и из хука `pre-push` (hk, `hk.pkl`). Отдельно `scripts/check.ps1` повторяет эту цепочку без mise. Изменяя одно звено, проверяйте всю цепочку, потому что версии инструментов зафиксированы в `mise.toml`, а `scripts/check.ps1` — отдельная копия последовательности.

**Тесты.** Бэкенд-тесты — API-тесты в `backend/src/test/.../*ApiTest` (реальный HTTP на случайном порту, чистая БД в памяти) и дымовой `SmokeTest`. Один тест: `cd backend && mvn -Dtest=SmokeTest test`. Тесты фронтенда — Vitest + Testing Library (`cd frontend && npm test`), решение в `docs/adr/0002`. Новые проверки сценариев добавляются в бэкенд-тесты рядом с кодом.

**Браузерный сценарий.** `e2e/` (Playwright, отдельный `package.json`): один сценарий «владелец создаёт тип встречи, гость записывается, второй гость получает конфликт, встреча видна владельцу». `playwright.config.ts` через `webServer` поднимает настоящий бэкенд (`mvn spring-boot:run`, порт 18080, БД `jdbc:h2:mem`, без профиля `dev`) и Vite (порт 15173, адрес бэкенда в `API_PROXY_TARGET`, см. `frontend/vite.config.ts`). Звено `e2e` в `mise.toml` стоит последним в `check` и зависит от `generate`. В `scripts/check.ps1` Maven подменяется через `E2E_MVN=mvnw.cmd`, в CI перед проверками ставятся системные библиотеки Chromium (`ci.yml`).

**Два способа запуска проверок.** `scripts/check.ps1` и `mise run check` повторяют одну и ту же последовательность, но `check.ps1` вызывает `./mvnw`, а mise — `mvn` из своего набора. Версии Maven держатся в `mise.toml` (3.10.1) и в `backend/.mvn/wrapper/maven-wrapper.properties`; при обновлении меняйте оба места.

**Docker и деплой.** `Dockerfile` собирает фронтенд (с генерацией SDK) и кладёт `dist` в `classpath:/static/` бэкенда, поэтому в образе один процесс на одном порту (`server.port=${PORT:8080}`). Статику и переход на `index.html` для маршрутов React Router делает `web/SpaConfig` (стандартная раздача Spring выключена); `/api/**` и пути с расширением файла остаются 404. В тестах вместо сборки лежит `src/test/resources/static/index.html`. Деплой — Render (`render.yaml`, MCP в `.mcp.json`).

**Файлы, которые не трогаем вручную:** `.github/workflows/hexlet-check.yml` (его генерирует Хекслет), `frontend/src/api/generated`, `backend/target`, `contract/openapi.yaml` (только через `mise run contract`).

**Данные.** Бэкенд хранит данные во встроенной H2 (файл `backend/data/calendar`, в `.gitignore`). Схема — `schema.sql`; демо-данные (два типа встречи) — `demo-data.sql`, подключаются только профилем `dev` (`mise daemons start backend` запускает с ним). Ошибки API — единый формат `{code, message}` (`error/ApiExceptionHandler`). Тесты бэкенда используют БД в памяти (`src/test/resources/application.properties`).
