# Календарь звонков (продолжение)


[![hexlet-check](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/bahaalmkz/ai-for-developers-project-387/actions)
[![ci](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/ci.yml/badge.svg)](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/ci.yml)

Интегрируйте работу агентов в GitHub проект

**Приложение: https://calendar-i9g4.onrender.com** (запись гостя: [/book](https://calendar-i9g4.onrender.com/book), раздел владельца: [/owner](https://calendar-i9g4.onrender.com/owner)). Если адрес не открывается, запустите приложение локально по командам из раздела [Запуск для проверки](#запуск-для-проверки).

## Откуда проект

Приложение перенесено из первого проекта «Календарь звонков»: [bahaalmkz/ai-for-developers-project-386](https://github.com/bahaalmkz/ai-for-developers-project-386). Вместе с ним перенесены `Dockerfile`, тесты, линтеры, GitHub Actions, `AGENTS.md`, скиллы (`.claude/skills/`) и их настройки (`docs/agents/`), словарь `GLOSSARY.md` и решения `docs/adr/`.

Новые задачи заводятся в Issues этого репозитория. Исходные спецификация и тикеты остались в первом репозитории:

- спецификация: [386#13 «Спецификация: бронирование звонков»](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/13);
- тикеты реализации: [386#14–#22](https://github.com/bahaalmkz/ai-for-developers-project-386/issues?q=is%3Aissue+label%3Aready-for-agent);
- карта решений и вопросы к ней: [386#2](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/2) (#3–#12).

Номера задач в истории коммитов и в `CHANGELOG.md` (`#14`–`#22`) относятся к первому репозиторию.

## Агент в GitHub

В репозитории работает агент [OpenCode](https://opencode.ai/docs/github/) через GitHub App [opencode-agent](https://github.com/apps/opencode-agent). Локально скиллы запускал Claude Code; в GitHub тот же набор скиллов и правил выполняет OpenCode на раннере Actions. Отличие только в агенте: OpenCode читает те же `.claude/skills/`, `AGENTS.md` и `docs/agents/`, а скилл вызывается текстом команды, а не слэш-командой чата.

| Воркфлоу | Событие | Модель | Назначение | Где результат |
| --- | --- | --- | --- | --- |
| [`opencode.yml`](.github/workflows/opencode.yml) | `issue_comment` и `pull_request_review_comment`, `types: [created]` | `opencode/deepseek-v4.1-flash-free` (бесплатная модель OpenCode) | ответ на команду в issue/PR, запуск скиллов, правки PR | комментарий от `opencode-agent[bot]` в том же issue/PR; лог прогона — [Actions → opencode](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode.yml) |

**Команды** (`mentions: /oc,/opencode`, набор по умолчанию, записан явно): комментарий, содержащий `/oc` или `/opencode`, например `/oc explain this issue` или `/oc запусти скилл to-spec по обсуждению этого issue`. Вопросы и решения остаются в обсуждении issue, следующая сессия читает их перед работой.

**Кто может вызвать.** Только владелец и участники репозитория (`author_association`: `OWNER`, `MEMBER`, `COLLABORATOR`). Комментарии ботов пропускаются, чтобы агент не отвечал сам себе. Редактирование комментария агента повторно не запускает (`types: [created]`). Комментарии к строкам diff в PR из fork агента не запускают; `pull_request_target` не используется, поэтому код чужого PR не получает ключ модели.

**Права.** Стандартная авторизация OpenCode: заданию выдано только `contents: read` и `id-token: write`, у `actions/checkout` — `persist-credentials: false`. Отвечает и пушит агент правами приложения opencode-agent.

**Ключ модели.** Секрет `OPENCODE_API_KEY` (Settings → Secrets and variables → Actions), ключ из [OpenCode Console](https://opencode.ai/docs/zen/). Модель бесплатная, но ключ нужен: по нему провайдер узнаёт аккаунт и считает лимиты. В файлах ключа нет.

**Ограничения и остановка.** Тайм-аут задания 20 минут; один прогон на issue/PR одновременно (`concurrency`, следующая команда ждёт). Остановить текущий прогон — Actions → прогон → **Cancel workflow run**; отключить будущие — Actions → opencode → **Disable workflow**; при утечке — отозвать ключ в OpenCode Console.

### Проверка работы

Команда в issue, например `/oc прочитай настройки из docs/agents/ и AGENTS.md. Назови: новый трекер задач (репозиторий), доступные тебе скиллы (есть ли среди них to-spec, to-tickets и implement-spec) и команду проверок проекта.` Агент отвечает комментарием от `opencode-agent[bot]`: трекер — Issues репозитория `bahaalmkz/ai-for-developers-project-387` (через `gh`), скиллы `to-spec`, `to-tickets`, `implement-spec` доступны (помечены `disable-model-invocation`, вызываются по имени командой), проверки — `mise run check`.

Ответ и прогон: [это issue](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/3) и [Actions → opencode](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode.yml).

## Запуск для проверки

Через Docker (нужен только Docker):

```bash
git clone https://github.com/bahaalmkz/ai-for-developers-project-387.git
cd ai-for-developers-project-387
docker build -t calendar .
docker run --rm -e PORT=8080 -p 8080:8080 calendar   # откройте http://localhost:8080
```

Без Docker нужны JDK 21 и Node 26 (или mise, версии в `mise.toml`) — см. разделы «Установка» и «Использование» ниже.

---

Учебный проект Хекслета: https://ru.hexlet.io/programs/ai-for-developers
Как это должно работать: https://files.hexlet.app/a/2ipc5m

Упрощённый сервис бронирования времени по мотивам [Cal.com](https://cal.com/). Владелец публикует типы встреч и свободное время, гость без регистрации выбирает тип встречи и записывается на свободный слот.

## Сценарии

### Владелец календаря

1. Открывает приложение и создаёт тип встречи: название, описание, длительность.
2. Видит список своих типов встреч.
3. Открывает список предстоящих встреч всех типов.
4. После того как гость записался, видит его встречу в этом списке.

### Гость

1. Открывает страницу записи без регистрации.
2. Выбирает тип встречи.
3. Открывает календарь на ближайшие 14 дней, начиная с сегодняшнего (сегодня показываются только слоты, которые ещё не начались).
4. Выбирает свободный слот. Его длительность равна длительности выбранного типа встречи.
5. Подтверждает запись и видит её в подтверждении.

Занятое время показывается как недоступное. Занять его повторно нельзя, даже другим типом встречи.

## Требования и ограничения

- Владелец один, задаётся заранее. Авторизации, личных кабинетов и регистрации нет.
- Интеграций с внешними календарями, уведомлений и напоминаний нет.
- Интервалы не пересекаются, включая частичное наложение разных типов встреч. Следующая встреча может начинаться в момент окончания предыдущей.
- Правила действуют и при двух одновременных запросах на одно время: один должен быть принят, второй отклонён.
- Расписание фиксировано и задаётся в конфигурации бэкенда, владелец его не меняет ([ADR 0003](docs/adr/0003-fixed-schedule-and-timezone.md)): слоты каждый день с 09:00 до 18:00, шаг стартов 30 минут, слот целиком помещается до 18:00. Единый часовой пояс `Asia/Almaty` (+05:00), время гостю показывается в нём с подписью пояса.
- Данные хранятся во встроенной БД H2 в файле `data/calendar` рядом с каталогом запуска ([ADR 0004](docs/adr/0004-embedded-h2-and-calendar-lock.md)). Отдельный сервер БД не нужен. Тесты и браузерный сценарий используют чистую БД в памяти.
- **Ограничение хранения.** Локально (`./mvnw spring-boot:run`) файл БД переживает перезапуск. В контейнере файл лежит внутри него (`/app/data`): новый контейнер, передеплой или перезапуск сервиса на Render (в том числе после засыпания бесплатного тарифа) начинают с пустой БД, типы встреч и записи сбрасываются. Чтобы данные переживали перезапуск контейнера, нужно подключить том на `/app/data` (`docker run -v calendar-data:/app/data ...`); на бесплатном тарифе Render постоянного диска нет.
- Приложение собирается в Docker-образ (`Dockerfile` в корне) и запускается в контейнере по порту из переменной `PORT`.

## Контракт API и генерация клиента

- API описывается контрактом на TypeSpec. Из него генерируется OpenAPI-спецификация.
- Клиентский SDK для фронтенда генерируется из OpenAPI. Выбранный язык фронтенда должен иметь такой генератор.
- Серверные типы и маршруты генерируются, если фреймворк это поддерживает. Если нет, бэкенд реализуется по контракту и проверяется тестами.

## Стек

- **Бэкенд:** Java, Spring Boot. Версии Java и Spring Boot фиксируются при создании проекта.
- **Контракт:** TypeSpec → OpenAPI. Серверные интерфейсы и модели генерируются из OpenAPI (openapi-generator, генератор `spring`).
- **Фронтенд:** React, Vite, TypeScript. SDK генерируется из OpenAPI генератором `typescript-fetch`.
- **Контейнер:** Docker-образ (`Dockerfile`), запуск в контейнере по `PORT`, деплой на Render.

## Установка

Нужны mise (версии JDK 21 и Node фиксирует `mise.toml`) и Git.

```bash
git clone https://github.com/bahaalmkz/ai-for-developers-project-387.git
cd ai-for-developers-project-387
mise install
npm ci           # commitlint для хука commit-msg
mise run check   # все проверки, как в GitHub Actions
```

Без mise (Windows) можно запустить тот же набор проверок скриптом:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check.ps1
```

## Использование

Фоновый запуск через mise (бэкенд с профилем `dev` и демо-данными: два типа встреч):

```bash
mise run generate                 # клиентский SDK фронтенда из контракта
mise daemons start                # backend :8080 и frontend :5173; статус — mise daemons status
```

Или вручную, в двух терминалах:

```bash
cd backend && ./mvnw spring-boot:run   # API на http://localhost:8080, проверка: /api/health
cd frontend && npm ci && npm run generate:api && npm run dev   # http://localhost:5173, /api проксируется на бэкенд
```

Страницы: `/` главная, `/book` запись гостя, `/owner` раздел владельца (типы встреч и предстоящие встречи).

## Docker

Один образ: Spring Boot отдаёт и API (`/api`), и собранный фронтенд. Клиентский SDK и серверные интерфейсы генерируются из `contract/openapi.yaml` внутри сборки.

```bash
docker build -t calendar .
docker run --rm -e PORT=8080 -p 8080:8080 calendar   # http://localhost:8080
bash scripts/smoke.sh http://localhost:8080           # сквозная проверка запущенного приложения
```

`scripts/smoke.sh` создаёт два типа встреч (60 и 30 минут), записывает гостя, проверяет запись в списке владельца и отказ (409) для пересекающейся записи другого типа. Тот же сценарий запускает задача `docker` в GitHub Actions.

## Деплой

**Публичная ссылка: https://calendar-i9g4.onrender.com** (запись гостя: [/book](https://calendar-i9g4.onrender.com/book), раздел владельца: [/owner](https://calendar-i9g4.onrender.com/owner)).

Сервис развёрнут на Render (бесплатный тариф, регион Frankfurt) в первом проекте: из `Dockerfile` ветки `main` репозитория [ai-for-developers-project-386](https://github.com/bahaalmkz/ai-for-developers-project-386) через MCP-сервер Render; каждый push в `main` того репозитория передеплоивает его автоматически. Код приложения здесь тот же, поэтому ссылка показывает это приложение. Проверка по ссылке: `bash scripts/smoke.sh https://calendar-i9g4.onrender.com`. Бесплатный сервис засыпает после 15 минут без запросов: первый запрос после паузы отвечает дольше, а данные после пробуждения сбрасываются (см. «Ограничение хранения»).

### MCP-сервер Render

Деплой выполняет агент через [Render MCP](https://render.com/docs/mcp-server). Конфигурация для Claude Code лежит в `.mcp.json`; ключа в ней нет, он берётся из переменной окружения.

1. В Render: Account Settings → API Keys → Create API Key.
2. Задайте ключ в переменной окружения, не в файлах репозитория:
   ```powershell
   setx RENDER_API_KEY "<ключ>"
   ```
   (в bash: `export RENDER_API_KEY=<ключ>`). Откройте новый терминал и перезапустите агента.
3. Агент подхватит сервер `render` из `.mcp.json` (Claude Code спросит разрешение на проектный MCP-сервер). Попросите выбрать рабочее пространство Render и создать веб-сервис из Docker-образа этого репозитория.

Без MCP такой же сервис создаётся вручную: Render → New → Blueprint → этот репозиторий (`render.yaml`).

<!-- Добавьте запись asciinema — именно это смотрит работодатель -->

---

<details>
<summary>Автоматические тесты Хекслета</summary>

Тесты запускаются на каждый коммит. За запуск отвечает файл `.github/workflows/hexlet-check.yml` — не удаляйте и не переименовывайте ни его, ни репозиторий.

</details>

## О Хекслете

[Хекслет](https://ru.hexlet.io/) — школа программирования: авторские программы обучения с практикой, поддержкой наставников и реальными проектами, которые остаются в резюме. Этот репозиторий — один из таких проектов.
