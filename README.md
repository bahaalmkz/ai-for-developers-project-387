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
| [`opencode.yml`](.github/workflows/opencode.yml) | `issue_comment` и `pull_request_review_comment`, `types: [created]` | `opencode/gpt-5-nano` (OpenCode Zen, оплата по факту: $0.05 / $0.40 за 1M токенов) | ответ на команду в issue/PR, запуск скиллов, правки PR; на PR — не больше 3 запусков агента, при исчерпании или сбое — комментарий «нужен человек» | комментарий от `opencode-agent[bot]` в том же issue/PR; лог прогона — [Actions → opencode](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode.yml) |
| [`opencode-triage.yml`](.github/workflows/opencode-triage.yml) | `issues`, `types: [opened]` | `opencode/gpt-5-nano` (бесплатный уровень Gemini API) | предварительный разбор нового обращения по скиллу `triage`: категория, состояние, метки, вопросы автору | метки и комментарий от `opencode-agent[bot]` в том же issue; лог прогона — [Actions → opencode-triage](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode-triage.yml) |
| [`opencode-review.yml`](.github/workflows/opencode-review.yml) | `pull_request`, `types: [opened, synchronize, reopened, ready_for_review]` | `gemini/gemini-flash-latest` (не та, что у разработчика; 5 запросов в минуту, 20 в день — хватает на ревью), запасная `gemini/gemini-flash-lite-latest` (бесплатный уровень Gemini API) | независимое авторевью каждого PR по `REVIEW.md`, спецификации и тикетам; только чтение и комментарий | комментарий «Авторевью для `<head SHA>`» от `github-actions[bot]` в PR; лог прогона — [Actions → opencode-review](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode-review.yml) |
| [`ci.yml`](.github/workflows/ci.yml) | `push` и `pull_request` | — | на `push` — `check` и `docker`; на `pull_request` — браузерный сценарий на точном head SHA PR | [Actions → ci](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/ci.yml); артефакт `browser-check-<head SHA>` с `head-sha.txt`, `result.txt` и трассировками |

**Команды** (`mentions: /oc,/opencode`, набор по умолчанию, записан явно): комментарий, содержащий `/oc` или `/opencode`, например `/oc explain this issue` или `/oc запусти скилл to-spec по обсуждению этого issue`. Вопросы и решения остаются в обсуждении issue, следующая сессия читает их перед работой.

**Кто может вызвать.** Только владелец и участники репозитория (`author_association`: `OWNER`, `MEMBER`, `COLLABORATOR`). Комментарии ботов пропускаются, чтобы агент не отвечал сам себе. Редактирование комментария агента повторно не запускает (`types: [created]`). Комментарии к строкам diff в PR из fork агента не запускают; `pull_request_target` не используется, поэтому код чужого PR не получает ключ модели.

**Запуск.** `opencode.yml` и `opencode-triage.yml` ставят OpenCode 1.19.6 из GitHub Releases с проверкой sha256 и запускают `opencode github run` — то же, что делает composite-action `anomalyco/opencode/github`. Сам action не используется: его шаг «Get opencode version» спрашивает версию у GitHub API без токена и регулярно падал на лимите анонимных запросов общего IP раннеров, а каждый такой сбой на PR расходовал попытку бюджета.

**Права.** На уровне воркфлоу прав нет (`permissions: {}`), у каждого задания только свои: `budget` — `actions: read`, чтобы прочитать историю прогонов; `agent` — `contents: read`, `issues: write` и `id-token: write`; `report` — `actions: read`, `issues: write`, `pull-requests: write`, чтобы разобрать лог и оставить комментарий. Стандартная авторизация OpenCode: токен GitHub App через OIDC, у `actions/checkout` — `persist-credentials: false`. Отвечает и пушит агент правами приложения opencode-agent. Команде `gh` внутри агента передан `GITHUB_TOKEN` (переменная `GH_TOKEN`) с правом `issues: write`: скиллы `to-spec`, `to-tickets` и `triage` создают issues, ставят метки и связи. Такие issues создаёт `github-actions[bot]`, и другие воркфлоу они не запускают.

**Провайдер Gemini.** Модели подключены в `opencode.json` как провайдер `gemini` через совместимый с OpenAI эндпоинт Gemini API (`@ai-sdk/openai-compatible`, `https://generativelanguage.googleapis.com/v1beta/openai`, ключ `{env:GOOGLE_GENERATIVE_AI_API_KEY}`). Встроенный провайдер `google` в OpenCode 1.19.6 передавал многошаговый диалог с вызовами инструментов так, что Gemini отклонял запрос (`400 Requests ending with a model turn are not supported`, PR #21); через совместимый с OpenAI API результаты инструментов уходят обычными сообщениями `tool`. Агенту-разработчику Gemini не подошёл: модели 3.x через совместимый с OpenAI API требуют `thought_signature` у вызовов инструментов (`400 Function call is missing a thought_signature`, issue #25), которую SDK в OpenCode 1.19.6 не передаёт, а `gemini-2.5-flash-lite` закрыта для новых пользователей. Поэтому агент и триаж — на `opencode/gpt-5-nano`: самая дешёвая модель OpenCode Zen с вызовом инструментов, оплата по факту с баланса аккаунта ($5), а ревьюер и служебная модель — на Gemini: им инструменты не нужны. Разные провайдеры у разработчика и ревьюера — тоже разные агенты.

**Служебная модель.** `opencode.json` задаёт `small_model` — `gemini/gemini-flash-lite-latest` из того же бесплатного уровня. Без этого OpenCode генерирует заголовок сессии моделью по умолчанию другого провайдера и получает ошибку (раньше — `Insufficient balance`).

**Ключи моделей.** Два секрета (Settings → Secrets and variables → Actions), в файлах ключей нет. `OPENCODE_API_KEY` из [OpenCode Console](https://opencode.ai/docs/zen/) — для агента и триажа на `opencode/gpt-5-nano` (оплата по факту с баланса Console; запуск агента — порядка цента). У бесплатных моделей OpenCode Zen общий дневной лимит запросов на аккаунт — на нём агент остановился посреди шага 4 (`429 RateLimit.FreeUsageLimitError` в Console, CLI при этом не завершается — ревью ловит это в логе). `GOOGLE_GENERATIVE_AI_API_KEY` из [Google AI Studio](https://aistudio.google.com/apikey) — для ревьюера и служебной модели на бесплатном уровне Gemini API (лимиты на каждую модель: Flash — 5 запросов в минуту и 20 в день, Flash Lite — 15 и 500).

**Ограничения и остановка.** Тайм-аут задания 20 минут; один прогон на issue/PR одновременно (`concurrency`, следующая команда ждёт). Остановить текущий прогон — Actions → прогон → **Cancel workflow run**; отключить будущие — Actions → opencode → **Disable workflow**; при утечке — отозвать ключ в OpenCode Console.

**Предел запусков на PR.** На один PR — не больше 3 запусков агента. Считается каждый допущенный запрос человека: общий комментарий (`issue_comment`) и комментарий к строке diff (`pull_request_review_comment`); отмена, тайм-аут и Re-run тоже расходуют попытку. Для обычных issues предела нет, новый PR получает новый бюджет. Состояние — история Actions, которую токен агента переписать не может; текст комментариев бюджет не меняет. Задание `budget` до вызова модели находит прогоны этого воркфлоу с тем же названием (`run-name: opencode #<номер>`) и считает задания `agent`, которые стартовали. Если бюджет исчерпан, модель не вызывается. В любом случае, когда бюджет исчерпан или задание агента не завершилось успехом, задание `report` пишет в тот же issue/PR комментарий «нужен человек» с причиной: `limit_exhausted` (бюджет исчерпан, использовано 3/3), `timeout` (превышено время задания), `infra` (сбой подготовки или не задан `GOOGLE_GENERATIVE_AI_API_KEY`, сессия агента не стартовала) или `agent_failed` (прочая ошибка, ссылка на лог). Человеку нужно проверить изменения и продолжить вручную; новый бюджет появится только у нового PR.

**Автотриаж.** `opencode-triage.yml` разбирает каждое новое обращение (`issues`, `types: [opened]`) без команды: инструкция передаётся во входе `prompt`, а ответ action публикует сам. Запускается только для доверенных авторов (`author_association`: `OWNER`, `MEMBER`, `COLLABORATOR`) и не для ботов. Issues с меткой `ready-for-agent` — тикеты и спецификации от `to-spec`/`to-tickets` — пропускаются, чтобы готовое не разбирали повторно. Агент проверяет, что уже умеет приложение, ставит одну метку категории (`bug`/`enhancement`) и одну метку состояния из `docs/agents/triage-labels.md`; если данных не хватает — `needs-info` и комментарий по шаблону Triage Notes с конкретными вопросами. Код не меняет и issue не закрывает. Тайм-аут задания 15 минут; одновременно идёт один разбор на весь репозиторий (`concurrency: opencode-triage`). Если обращений сразу несколько, GitHub держит в очереди только одно ожидающее, более раннее ожидающее отменяется — его разбирают вручную командой `/oc`. Метки ставит `gh` внутри агента (`GH_TOKEN`, `issues: write`), комментарий публикует action от `opencode-agent[bot]`.

**Авторевью.** `opencode-review.yml` запускается на каждый PR этого репозитория (`pull_request`, `types: [opened, synchronize, reopened, ready_for_review]`), кроме черновиков; PR из fork пропускаются. `pull_request_target` не используется, поэтому код чужого PR не получает ключ модели. Новая версия PR отменяет устаревшее ревью (`concurrency` с `cancel-in-progress: true`), тайм-аут задания 15 минут. Права: на уровне воркфлоу `permissions: {}`, у задания только необходимые — `contents` (checkout и `REVIEW.md`), `issues` (связанные issues), `actions`, `checks` и `statuses` (статусы CI) на чтение и `pull-requests: write` для комментария; `id-token` не нужен, GitHub App и OIDC здесь не используются. Сбор контекста (`gh pr view`, `gh pr diff`, тексты issues, `gh pr checks`) и публикация комментария идут без модели. Ревью делается за один шаг модели (`opencode run --agent build`): контекст склеивается в один файл и передаётся через stdin, а `OPENCODE_PERMISSION` запрещает модели все инструменты — чтение файлов, поиск, правку, bash, webfetch, вопросы. Так ревьюер видит ровно то, что ему передали, и укладывается во время: бесплатная модель тратит 30–60 с на шаг, и с чтением файлов ревью не успевало за 12 минут. Ревьюер работает на другой модели, чем агент-разработчик: `gemini-flash-latest` (4 минуты), при неудаче — `gemini-flash-lite-latest` (5 минут). У бесплатного уровня Gemini лимиты на каждую модель: Flash — 5 запросов в минуту и 20 в день, Flash Lite — 15 и 500. Агент делает запрос на каждый шаг, поэтому он на Flash Lite; ревью — один запрос на версию PR, поэтому ревьюеру хватает Flash. На Flash агент получал `model is currently experiencing high demand`. Если обе попытки через OpenCode не дали текста (на большом контексте Gemini иногда заканчивает первый шаг без ответа, и OpenCode получает «Requests ending with a model turn are not supported»), тот же запрос уходит одним вызовом Gemini API через `curl` — ревьюеру инструменты не нужны. Ошибку провайдера ревью ловит в логе CLI и сразу останавливается — в комментарии причина `infra` и текст ошибки; модель указана в комментарии. Критерии — `REVIEW.md` в корне (занятость слотов и 409, время и окно записи, контракт API, формат ошибок, совместимость данных H2, тесты, объём, шаблон PR). Ревьюер не правит код: правку делает `opencode.yml` по команде `/oc` человека. Комментарий «Авторевью для `<head SHA>`» публикует `github-actions[bot]`; если модель не вернула текст, в комментарии причина и ссылка на лог прогона. Фильтра по автору-боту нет: PR агента тоже ревьюится.
**Браузерная проверка PR.** На `pull_request` задание `browser` в `ci.yml` (без секретов, `contents: read`, тайм-аут 30 минут) проверяет точный head SHA PR, а не merge-коммит: `actions/checkout` получает `github.event.pull_request.head.sha`. Фактический `git rev-parse HEAD` пишется в `e2e/artifacts/head-sha.txt` и в summary прогона, `mise run e2e` запускается с `E2E_TRACE=on` (трассировка и скриншоты для любого исхода), а код выхода и полный вывод — в `e2e/artifacts/result.txt`. Артефакт `browser-check-<head SHA>` с каталогами `e2e/artifacts/` и `e2e/test-results/` сохраняется всегда, даже при падении. Перед мержем сверьте SHA из артефакта с текущим head PR (`gh pr view --json headRefOid`): если они разошлись, браузерная проверка относилась к старому коммиту и её нужно перезапустить.

### Проверка работы

Команда в issue, например `/oc прочитай настройки из docs/agents/ и AGENTS.md. Назови: новый трекер задач (репозиторий), доступные тебе скиллы (есть ли среди них to-spec, to-tickets и implement-spec) и команду проверок проекта.` Агент отвечает комментарием от `opencode-agent[bot]`: трекер — Issues репозитория `bahaalmkz/ai-for-developers-project-387` (через `gh`), скиллы `to-spec`, `to-tickets`, `implement-spec` доступны (помечены `disable-model-invocation`, вызываются по имени командой), проверки — `mise run check`.

Ответ и прогон: [это issue](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/3) и [Actions → opencode](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/workflows/opencode.yml).

**Автотриаж** проверен на двух issues. Обычное обращение [#13](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/13) разобрано без команды: метки `enhancement` и `needs-info`, комментарий с вопросами, [прогон](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/runs/45415874086). Issue [#14](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/14) с меткой `ready-for-agent` пропущено: [прогон](https://github.com/bahaalmkz/ai-for-developers-project-387/actions/runs/45415881266) со статусом `skipped`.

Воркфлоу (`.github/workflows/`) агент написать может, а запушить — нет: GitHub не даёт GitHub App менять воркфлоу без права `workflows`, у opencode-agent его нет. Такие изменения переносит в PR человек (так сделан [#12](https://github.com/bahaalmkz/ai-for-developers-project-387/pull/12) из ответа агента в [#11](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/11)).

## От обращения к спецификации

Доработки начинаются с обращения в Issues, сформулированного словами пользователя, и проходят тот же путь, что в первом проекте, но в GitHub: разбор (`triage`) → вопросы и ответы → `to-spec` → `to-tickets` → согласование человеком → `implement-spec`.

Пример — тема разговора в записи гостя:

1. Обращение: [#6 «Хочу заранее знать, о чём гость хочет поговорить»](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/6).
2. [Разбор агента](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/6#issuecomment-7133330978) (что уже умеет приложение, вопросы) и [ответы](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/6#issuecomment-7133369863).
3. Спецификация (`to-spec`): [#8](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/8) — основной, негативный и соседний сценарии, изменение API через TypeSpec.
4. Тикеты (`to-tickets`): [#9](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/9) и [#10](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/10) (под-issues #8, #10 заблокирован #9).
5. [Согласование](https://github.com/bahaalmkz/ai-for-developers-project-387/issues/6#issuecomment-7133838426) спецификации и тикетов в исходном обращении; PR реализации ссылается на этот комментарий.

Метки разбора — `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix` (`docs/agents/triage-labels.md`). Спецификации и тикеты от скиллов получают `ready-for-agent` и автотриажем не разбираются.

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
