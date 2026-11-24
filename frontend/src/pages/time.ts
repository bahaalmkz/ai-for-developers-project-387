// Часовой пояс владельца (ADR 0003): время всегда показываем в нём
export const OWNER_TIMEZONE = 'Asia/Almaty'

// Окно записи в днях (ADR 0003, calendar.schedule.window-days на бэкенде)
export const WINDOW_DAYS = 14

export const dayFormat = new Intl.DateTimeFormat('ru-RU', {
  timeZone: OWNER_TIMEZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})

export const timeFormat = new Intl.DateTimeFormat('ru-RU', {
  timeZone: OWNER_TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
})

// Календарные дни считаем ключами 'YYYY-MM-DD' в поясе владельца, не в поясе браузера
const keyFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: OWNER_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** Ключ дня 'YYYY-MM-DD' в поясе владельца. */
export function dayKey(date: Date): string {
  return keyFormat.format(date)
}

// Арифметика ключей идёт в UTC: так нет сдвигов из-за пояса браузера
function keyToUtc(key: string): Date {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

export function addDays(key: string, days: number): string {
  const date = keyToUtc(key)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const keyDayFormat = new Intl.DateTimeFormat('ru-RU', {
  timeZone: 'UTC',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})

/** «понедельник, 23 ноября» для ключа дня; совпадает с dayFormat для слотов этого дня. */
export function dayLabel(key: string): string {
  return keyDayFormat.format(keyToUtc(key))
}

const monthFormat = new Intl.DateTimeFormat('ru-RU', {
  timeZone: 'UTC',
  month: 'long',
  year: 'numeric',
})

/** Месяц задаётся ключом 'YYYY-MM'. */
export function monthLabel(month: string): string {
  return monthFormat.format(keyToUtc(`${month}-01`))
}

export function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return new Date(Date.UTC(year, monthNumber - 1 + delta, 1)).toISOString().slice(0, 7)
}

/** Ячейки месяца с понедельника: null — пустая ячейка до первого числа. */
export function monthCells(month: string): (string | null)[] {
  const [year, monthNumber] = month.split('-').map(Number)
  const first = new Date(Date.UTC(year, monthNumber - 1, 1))
  const offset = (first.getUTCDay() + 6) % 7
  const days = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()
  const cells: (string | null)[] = Array.from({ length: offset }, () => null)
  for (let day = 1; day <= days; day++) {
    cells.push(`${month}-${String(day).padStart(2, '0')}`)
  }
  return cells
}
