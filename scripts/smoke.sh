#!/usr/bin/env bash
# Сквозная проверка запущенного приложения по адресу (контейнер локально, в CI или публичная ссылка):
# страницы открываются, владелец создаёт типы встреч, гость записывается, запись видна владельцу,
# пересекающаяся запись другого типа (60 минут в S и 30 минут в S+30) отклоняется с 409.
#
#   scripts/smoke.sh http://localhost:8080
#
# Создаёт настоящие данные: типы встреч с уникальным id и одну запись.
set -euo pipefail

BASE="${1:?Укажите адрес, например http://localhost:8080}"
BASE="${BASE%/}"
RUN_ID="smoke-$(date +%s)"

fail() { echo "FAIL: $*" >&2; exit 1; }

# request METHOD PATH [BODY] -> печатает тело, код ответа кладёт в $STATUS
request() {
  local out
  # Тело через stdin: в аргументах командной строки Windows кириллица портится
  out=$(printf '%s' "${3:-}" | curl -sS -X "$1" "$BASE$2" -H 'Content-Type: application/json' \
    ${3:+--data-binary @-} -w $'\n%{http_code}')
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

expect() { [ "$STATUS" = "$1" ] || fail "$2: ожидался $1, пришёл $STATUS: $BODY"; echo "ok   $2 ($STATUS)"; }

request GET /api/health;  expect 200 "GET /api/health"
for page in / /book /owner; do
  request GET "$page"; expect 200 "страница $page"
  grep -q '<div id="root">' <<<"$BODY" || fail "страница $page: нет корня React"
done

request POST /api/event-types "{\"id\":\"$RUN_ID-60\",\"title\":\"Проверка 60 минут\",\"durationMinutes\":60}"
expect 201 "создание типа встречи на 60 минут"
request POST /api/event-types "{\"id\":\"$RUN_ID-30\",\"title\":\"Проверка 30 минут\",\"durationMinutes\":30}"
expect 201 "создание типа встречи на 30 минут"

request GET "/api/event-types/$RUN_ID-60/slots"; expect 200 "свободные слоты"
START=$(grep -o '"start":"[^"]*"' <<<"$BODY" | head -1 | cut -d'"' -f4)
[ -n "$START" ] || fail "нет свободных слотов: $BODY"
echo "     первый свободный слот: $START"

request POST /api/bookings "{\"eventTypeId\":\"$RUN_ID-60\",\"start\":\"$START\",\"guestName\":\"Smoke\",\"guestEmail\":\"smoke@example.com\"}"
expect 201 "запись гостя на 60 минут"
BOOKING_ID=$(grep -o '"id":"[^"]*"' <<<"$BODY" | head -1 | cut -d'"' -f4)

# Через 30 минут после начала: частичное наложение встречей другого типа
OVERLAP=$(date -u -d "@$(( $(date -d "$START" +%s) + 1800 ))" +%Y-%m-%dT%H:%M:%SZ)
request POST /api/bookings "{\"eventTypeId\":\"$RUN_ID-30\",\"start\":\"$OVERLAP\",\"guestName\":\"Smoke 2\",\"guestEmail\":\"smoke2@example.com\"}"
expect 409 "пересекающаяся запись другого типа отклонена"
grep -q '"code":"conflict"' <<<"$BODY" || fail "в ответе на конфликт нет code=conflict: $BODY"

request GET /api/bookings; expect 200 "список встреч владельца"
grep -q "\"id\":\"$BOOKING_ID\"" <<<"$BODY" || fail "запись $BOOKING_ID не видна владельцу"
echo "ok   запись $BOOKING_ID есть в списке владельца"

echo "Все проверки прошли: $BASE"
