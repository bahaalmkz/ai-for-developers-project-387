# AGENTS.md

## Команды

- `mise run check` — все проверки (линтеры, сборка фронтенда, тесты бэкенда и фронтенда, браузерный сценарий Playwright). Это же вызывает GitHub Actions.
- `powershell -ExecutionPolicy Bypass -File scripts\check.ps1` — то же самое без mise (Windows, если mise заблокирован). Версии в этом случае берутся из установленных Node и JDK.
- `mise run contract` — компиляция контракта `contract/main.tsp` в `contract/openapi.yaml`.
- `mise run generate` — генерация клиента фронтенда из OpenAPI (`frontend/src/api/generated`).
- `mise run lint`, `mise run test`, `mise run typecheck` — отдельные этапы.
- Один тест бэкенда: `cd backend && mvn -Dtest=SmokeTest test`. Тесты фронтенда: `cd frontend && npm test` (Vitest).
- `mise run e2e` — браузерный сценарий Playwright (`e2e/`, версия закреплена в `e2e/package.json`). Сам поднимает бэкенд на порту 18080 (чистая БД в памяти, без профиля dev) и фронтенд на 15173, поэтому демоны для него не нужны. Хромиум ставится автоматически. Входит в `mise run check`, `scripts/check.ps1` и CI.
- `mise daemons start|status|logs|stop` — backend (8080) и frontend (5173) в фоне через pitchfork.
- `docker build -t calendar .` и `docker run --rm -e PORT=8080 -p 8080:8080 calendar` — образ всего приложения (`Dockerfile`). `bash scripts/smoke.sh <адрес>` — сквозная проверка запущенного приложения (локальный контейнер или публичная ссылка). В CI это отдельная задача `docker`, в `mise run check` не входит.
- Деплой: Render, `render.yaml`. MCP-сервер Render описан в `.mcp.json`, ключ только в переменной окружения `RENDER_API_KEY`, в файлы его не записываем.
- Git-хуки (hk, `hk.pkl`): pre-commit запускает `mise run lint`, pre-push запускает `mise run check`.

## Устройство

Как части связаны между собой (контракт → бэкенд и SDK фронтенда → проверки) — в CLAUDE.md.

- `contract/` — TypeSpec-контракт. Источник правды для API. Меняем API здесь, затем `mise run contract`.
- `backend/` — Java 21, Spring Boot. Серверные интерфейсы генерируются из `contract/openapi.yaml` при сборке. Реализуем их в контроллерах.
- `frontend/` — React + Vite + TypeScript. Клиент API генерируется, руками не правим `src/api/generated`.

### Генерируемые файлы (руками не правим)

- `contract/openapi.yaml` — из `contract/main.tsp` командой `mise run contract`. Коммитится; CI после `mise run check` проверяет, что он совпадает с генерацией.
- `frontend/src/api/generated/` — клиентский SDK (`typescript-fetch`), `mise run generate`. Не коммитится, CI генерирует его заново.
- `backend/target/generated-sources/` — серверные интерфейсы и модели (`openapi-generator-maven-plugin`), создаются при сборке Maven. Не коммитятся.
- `CHANGELOG.md` и `.release-please-manifest.json` — ведёт release-please.

Меняется API — правим `contract/main.tsp`, перегенерируем, затем код обеих сторон. Фронтенд обращается к API только через сгенерированный клиент (`frontend/src/api/client.ts`).

## Правила

- Коммиты по Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:` и т. д. Проверяет хук commit-msg (commitlint, правила в `commitlint.config.mjs`). После клонирования один раз `npm ci` в корне, иначе хук не найдёт commitlint.
- Коммит реализации ссылается на тикет, который закрывает, номером в конце заголовка: `feat: запись на свободный слот (#18)`. GitHub превращает номер в ссылку, и по истории видно, какой коммит какую задачу делал. Коммиты без тикета (`docs:`, `chore:` вне спецификации) номер не требуют.
- Перед коммитом запускать `mise run check`.
- Критерии ревью PR — `REVIEW.md` в корне; шаблон PR — `.github/pull_request_template.md` (связанные issues, проверки, риск, откат).
- Не править `.github/workflows/hexlet-check.yml`.

## Релизы

- Релизы делает release-please (`.github/workflows/release-please.yml`, действие `googleapis/release-please-action` v5.0.0, закреплено по SHA). Конфигурация: `release-please-config.json`, текущая версия: `.release-please-manifest.json`.
- На каждый push в `main` действие разбирает Conventional Commits с прошлого релиза и открывает (обновляет) PR «chore(main): release X.Y.Z» с новой версией и `CHANGELOG.md`. Слияние этого PR создаёт git-тег `vX.Y.Z` и GitHub Release. Версию и `CHANGELOG.md` руками не правим.
- Правила версии: `fix:` — patch, `feat:` — minor, `feat!:` или `BREAKING CHANGE:` — major (пока версия 0.x, breaking даёт minor из-за `bump-minor-pre-major`). `docs:`, `chore:`, `test:` и т. п. версию не меняют.
- Выбран один пакет на весь репозиторий (тип `simple`, тег без компонента), а не раздельные `backend` и `frontend`: это один продукт с общим контрактом, API и клиент меняются одним коммитом, раздельные версии только усложнили бы историю. Версии в `pom.xml` и `package.json` с релизом не синхронизируются.
- Один раз в настройках репозитория: Settings → Actions → General → «Allow GitHub Actions to create and approve pull requests». С дефолтным `GITHUB_TOKEN` CI на PR релиза не запускается; при необходимости передать PAT в параметре `token`.

## Скиллы и агентная среда

Инженерные скиллы из `mattpocock/skills` установлены в `.claude/skills/` (агент Claude Code). Настройка под репозиторий записана в `docs/agents/`:

- `docs/agents/issue-tracker.md` — трекер: GitHub Issues этого репозитория (`bahaalmkz/ai-for-developers-project-387`) через `gh`. Спецификация и тикеты первого проекта остались в `bahaalmkz/ai-for-developers-project-386`, ссылки на них — в том же файле. Номера `#14`–`#22` в истории коммитов и `CHANGELOG.md` относятся к старому репозиторию.
- `docs/agents/triage-labels.md` — метки разбора задач (значения по умолчанию).
- `docs/agents/domain.md` — документы предметной области: один `GLOSSARY.md` и ADR в `docs/adr/` (single-context).

Скиллы `/to-spec`, `/to-tickets`, `/implement-spec` и `/setup-matt-pocock-skills` помечены `disable-model-invocation`: агент их сам не запускает и в списке доступных не показывает. Их вызывает пользователь командой со слэшем в чате. Остальные скиллы (`domain-modeling`, `tdd`, `code-review`, `pr` и др.) агент может подобрать сам по описанию или по просьбе. Перед реализацией читайте спецификацию, связанные тикеты и их зависимости.

### Агент в GitHub Actions (OpenCode)

- `.github/workflows/opencode.yml` запускает OpenCode по комментарию `/oc` или `/opencode` доверенного участника в issue или PR. Описание — README.md, раздел «Агент в GitHub».
- `.github/workflows/opencode-triage.yml` автоматически разбирает каждое новое обращение (`issues`, `types: [opened]`) по скиллу `triage`: ставит метки категории и состояния, при нехватке данных — `needs-info` и вопросы. Только для доверенных авторов и не для ботов; issues с меткой `ready-for-agent` пропускаются. Описание — README.md, раздел «Агент в GitHub».
- `.github/workflows/lighthouse-audit.yml` — аудит страниц раз в сутки и по кнопке: Lighthouse CI → сводка `scripts/lighthouse-summary.mjs` → агент заводит issue только по новой находке (строка `audit-key` в теле) и пишет вывод в журнал аудита (issue с меткой `audit-log`). Отчёты — в `.audit/` (не коммитится). Описание — README.md, раздел «Регулярный аудит Lighthouse».
- `.github/workflows/opencode-review.yml` — авторевью каждого PR этого репозитория по `REVIEW.md`: сбор контекста и публикация комментария без модели, модель только читает код и `.review/`. Описание — README.md, раздел «Агент в GitHub».
- OpenCode читает скиллы из `.claude/skills/`, правила из этого файла и настройки трекера из `docs/agents/`. Скилл вызывается текстом: `/oc запусти скилл to-spec по обсуждению этого issue`. Пометка `disable-model-invocation` в OpenCode только скрывает скилл из списка; по имени он загружается.
- На раннере нет mise-демонов и локальной БД. Проверки в GitHub делает `ci.yml` (`mise run check`) на каждый push, в том числе в ветки агента.
- Новый воркфлоу описывается в той же таблице README.md (событие, модель, назначение, где результат).
