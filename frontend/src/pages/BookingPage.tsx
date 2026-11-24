import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { bookingsApi, eventTypesApi, slotsApi } from '../api/client'
import { ResponseError, type ApiError, type EventType, type Slot } from '../api/generated'
import {
  OWNER_TIMEZONE,
  WINDOW_DAYS,
  addDays,
  dayKey,
  dayLabel,
  monthCells,
  monthLabel,
  shiftMonth,
  timeFormat,
} from './time'

type State =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; eventTypes: EventType[] }

type SlotsState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; byDay: Map<string, Slot[]> }

// После выбора типа: сначала слот, затем данные гостя
type Step = 'slot' | 'details'

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest} мин`
  if (rest === 0) return `${hours} ч`
  return `${hours} ч ${rest} мин`
}

function formatRange(slot: Slot): string {
  return `${timeFormat.format(slot.start)} – ${timeFormat.format(slot.end)}`
}

function groupByDay(slots: Slot[]): Map<string, Slot[]> {
  const byDay = new Map<string, Slot[]>()
  for (const slot of slots) {
    const key = dayKey(slot.start)
    const day = byDay.get(key)
    if (day) day.push(slot)
    else byDay.set(key, [slot])
  }
  return byDay
}

function Host() {
  return (
    <div className="host">
      <span className="host__avatar" aria-hidden="true">
        В
      </span>
      <div>
        <p className="host__name">Владелец календаря</p>
        <p className="host__role">Организатор</p>
      </div>
    </div>
  )
}

const emptyGuest = { guestName: '', guestEmail: '' }

export function BookingPage() {
  const navigate = useNavigate()
  const [state, setState] = useState<State>({ status: 'loading' })
  const [slots, setSlots] = useState<SlotsState>({ status: 'idle' })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [selectedDay, setSelectedDay] = useState<string | null>(null)
  const [month, setMonth] = useState<string | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null)
  const [step, setStep] = useState<Step>('slot')
  const [guest, setGuest] = useState(emptyGuest)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const latestSlotsRequest = useRef(0)
  // Сообщение о конфликте живёт рядом со слотами: после конфликта гость снова выбирает время
  const [conflict, setConflict] = useState<string | null>(null)

  function resetChoice() {
    setSelectedSlot(null)
    setFieldErrors({})
    setFormError(null)
    setConflict(null)
    setStep('slot')
  }

  function selectEventType(id: string) {
    setSelectedId(id)
    setSelectedDay(null)
    setMonth(null)
    resetChoice()
    setSlots({ status: 'loading' })
    loadSlots(id, null)
  }

  function backToEventTypes() {
    // Ответ слотов, который ещё в пути, больше не нужен
    latestSlotsRequest.current++
    setSelectedId(null)
    setSlots({ status: 'idle' })
    resetChoice()
  }

  function loadSlots(id: string, keepDay: string | null) {
    // Устаревшие ответы (после смены типа встречи) отбрасываем
    const request = ++latestSlotsRequest.current
    const isStale = () => request !== latestSlotsRequest.current
    return slotsApi
      .listSlots({ id })
      .then((list) => {
        if (isStale()) return
        const byDay = groupByDay(list)
        const day = keepDay && byDay.has(keepDay) ? keepDay : ([...byDay.keys()][0] ?? null)
        setSlots({ status: 'ready', byDay })
        setSelectedDay(day)
        setMonth((day ?? dayKey(new Date())).slice(0, 7))
      })
      .catch(() => {
        if (!isStale()) setSlots({ status: 'error' })
      })
  }

  function selectDay(key: string) {
    setSelectedDay(key)
    setSelectedSlot(null)
    setConflict(null)
  }

  function selectSlot(slot: Slot) {
    setSelectedSlot(slot)
    setFieldErrors({})
    setFormError(null)
    setConflict(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!selectedId || !selectedSlot) return
    setSubmitting(true)
    setFieldErrors({})
    setFormError(null)
    try {
      const booking = await bookingsApi.createBooking({
        createBookingRequest: {
          eventTypeId: selectedId,
          start: selectedSlot.start,
          guestName: guest.guestName,
          guestEmail: guest.guestEmail,
        },
      })
      navigate('/book/confirmation', { state: { booking } })
    } catch (error) {
      if (error instanceof ResponseError) {
        const body = (await error.response.json().catch(() => null)) as ApiError | null
        if (error.response.status === 400 && body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
          setFieldErrors(body.fieldErrors)
          return
        }
        if (error.response.status === 409) {
          // Слот уже занят: возвращаем к выбору времени и обновляем список
          setConflict(body?.message ?? 'Это время уже занято, выберите другой слот')
          setSelectedSlot(null)
          setStep('slot')
          await loadSlots(selectedId, selectedDay)
          return
        }
        setFormError(body?.message ?? 'Не удалось записаться')
      } else {
        setFormError('Не удалось записаться')
      }
    } finally {
      setSubmitting(false)
    }
  }

  function guestField(name: keyof typeof emptyGuest, label: string, type: string) {
    const error = fieldErrors[name]
    const errorId = `${name}-error`
    return (
      <div className="field">
        <label>
          {label}
          <input
            type={type}
            value={guest[name]}
            onChange={(e) => setGuest((current) => ({ ...current, [name]: e.target.value }))}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
          />
        </label>
        {error && (
          <span id={errorId} className="field-error">
            {error}
          </span>
        )}
      </div>
    )
  }

  useEffect(() => {
    let cancelled = false
    eventTypesApi
      .listEventTypes()
      .then((eventTypes) => {
        if (!cancelled) setState({ status: 'ready', eventTypes })
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'error' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  function renderCalendar() {
    const byDay = slots.status === 'ready' ? slots.byDay : new Map<string, Slot[]>()
    // Окно записи: 14 дней с сегодняшнего (в поясе владельца) плюс дни, где есть слоты
    const today = dayKey(new Date())
    const windowDays = new Set(Array.from({ length: WINDOW_DAYS }, (_, i) => addDays(today, i)))
    for (const key of byDay.keys()) windowDays.add(key)
    const sorted = [...windowDays].sort()
    const firstMonth = sorted[0].slice(0, 7)
    const lastMonth = sorted[sorted.length - 1].slice(0, 7)
    const current = month ?? firstMonth

    return (
      <section className="card calendar-card" aria-labelledby="calendar-title">
        <div className="calendar-head">
          <div>
            <h2 id="calendar-title">Календарь</h2>
            <p className="muted month-label">{monthLabel(current)}</p>
          </div>
          <div className="calendar-nav">
            <button
              type="button"
              className="icon-button"
              aria-label="Предыдущий месяц"
              disabled={current <= firstMonth}
              onClick={() => setMonth(shiftMonth(current, -1))}
            >
              ←
            </button>
            <button
              type="button"
              className="icon-button"
              aria-label="Следующий месяц"
              disabled={current >= lastMonth}
              onClick={() => setMonth(shiftMonth(current, 1))}
            >
              →
            </button>
          </div>
        </div>
        <div role="group" aria-label="Календарь" className="calendar-grid">
          {WEEKDAYS.map((weekday) => (
            <span key={weekday} className="calendar-weekday" aria-hidden="true">
              {weekday}
            </span>
          ))}
          {monthCells(current).map((key, index) => {
            if (!key) return <span key={`empty-${index}`} aria-hidden="true" />
            const number = Number(key.slice(8))
            if (!windowDays.has(key)) {
              return (
                <span key={key} className="cal-day cal-day--outside" aria-hidden="true">
                  {number}
                </span>
              )
            }
            const count = byDay.get(key)?.length ?? 0
            const classes = ['cal-day']
            if (key === today) classes.push('cal-day--today')
            return (
              <button
                key={key}
                type="button"
                className={classes.join(' ')}
                aria-pressed={key === selectedDay}
                aria-label={
                  count > 0
                    ? `${dayLabel(key)}, свободно: ${count}`
                    : `${dayLabel(key)}, нет свободного времени`
                }
                disabled={count === 0}
                onClick={() => selectDay(key)}
              >
                <span className="cal-day__number">{number}</span>
                {count > 0 && <span className="cal-day__count">{count} св.</span>}
              </button>
            )
          })}
        </div>
      </section>
    )
  }

  const selectedEventType =
    state.status === 'ready' ? state.eventTypes.find((e) => e.id === selectedId) : undefined

  if (!selectedEventType) {
    return (
      <main className="page">
        <section className="card host-card">
          <Host />
          <h1>Выберите тип встречи</h1>
          <p className="muted">Нажмите на карточку, чтобы открыть календарь и выбрать удобное время.</p>
        </section>
        {state.status === 'loading' && <p className="muted">Загрузка…</p>}
        {state.status === 'error' && (
          <p role="alert" className="alert">
            Не удалось загрузить типы встреч. Попробуйте обновить страницу.
          </p>
        )}
        {state.status === 'ready' && state.eventTypes.length === 0 && (
          <p className="muted">Типов встреч пока нет.</p>
        )}
        {state.status === 'ready' && state.eventTypes.length > 0 && (
          <ul aria-label="Типы встреч" className="plain-list event-types">
            {state.eventTypes.map((eventType) => (
              <li key={eventType.id} className="event-card">
                <div className="event-card__head">
                  <h2>{eventType.title}</h2>
                  <span className="badge">{formatDuration(eventType.durationMinutes)}</span>
                </div>
                {eventType.description && <p className="muted">{eventType.description}</p>}
                {/* Кнопка растянута на всю карточку: кликается карточка целиком */}
                <button
                  type="button"
                  className="event-card__select"
                  aria-label={`Выбрать ${eventType.title}`}
                  onClick={() => selectEventType(eventType.id)}
                />
              </li>
            ))}
          </ul>
        )}
        <Link to="/" className="back-link">
          На главную
        </Link>
      </main>
    )
  }

  const daySlots =
    slots.status === 'ready' && selectedDay ? (slots.byDay.get(selectedDay) ?? []) : []
  // Ошибка старта не относится к полям ввода: показываем её у формы
  const startError = fieldErrors.start

  return (
    <main className="page">
      <div className={step === 'slot' ? 'booking-grid' : 'booking-grid booking-grid--details'}>
        <aside className="card booking-info">
          <Host />
          <div className="booking-info__title">
            <h1>{selectedEventType.title}</h1>
            <span className="badge">{formatDuration(selectedEventType.durationMinutes)}</span>
          </div>
          {selectedEventType.description && (
            <p className="muted">{selectedEventType.description}</p>
          )}
          <div className="info-box">
            <span className="info-box__label">Выбранная дата</span>
            <span className="info-box__value">
              {selectedDay ? dayLabel(selectedDay) : 'Дата не выбрана'}
            </span>
          </div>
          <div className="info-box">
            <span className="info-box__label">Выбранное время</span>
            <span className="info-box__value">
              {selectedSlot ? formatRange(selectedSlot) : 'Время не выбрано'}
            </span>
          </div>
        </aside>

        {step === 'slot' && (
          <>
            {renderCalendar()}
            <section aria-label="Свободные слоты" className="card slots-card">
              <div>
                <h2>Свободное время</h2>
                <p className="muted small">
                  Часовой пояс владельца: {OWNER_TIMEZONE} (UTC+5)
                </p>
              </div>
              {conflict && (
                <p role="alert" className="alert">
                  {conflict}
                </p>
              )}
              {slots.status === 'loading' && <p className="muted">Загрузка слотов…</p>}
              {slots.status === 'error' && (
                <p role="alert" className="alert">
                  Не удалось загрузить слоты. Попробуйте ещё раз.
                </p>
              )}
              {slots.status === 'ready' && slots.byDay.size === 0 && (
                <p className="muted">Свободных слотов нет.</p>
              )}
              {selectedDay && daySlots.length > 0 && (
                <div role="group" aria-label={dayLabel(selectedDay)}>
                  <ul className="plain-list slot-list">
                    {daySlots.map((slot) => (
                      <li key={slot.start.toISOString()}>
                        <button
                          type="button"
                          className="slot-row"
                          aria-label={timeFormat.format(slot.start)}
                          aria-pressed={selectedSlot?.start.getTime() === slot.start.getTime()}
                          onClick={() => selectSlot(slot)}
                        >
                          <span>{formatRange(slot)}</span>
                          <span className="slot-row__status">Свободно</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="actions">
                <button type="button" className="button button--secondary" onClick={backToEventTypes}>
                  Назад
                </button>
                <button
                  type="button"
                  className="button"
                  disabled={!selectedSlot}
                  onClick={() => setStep('details')}
                >
                  Продолжить
                </button>
              </div>
            </section>
          </>
        )}

        {step === 'details' && (
          <form aria-label="Данные гостя" onSubmit={submit} noValidate className="card form">
            <h2>Ваши данные</h2>
            <p className="muted">Регистрация не нужна: достаточно имени и email.</p>
            {guestField('guestName', 'Имя', 'text')}
            {guestField('guestEmail', 'Email', 'email')}
            {(startError || formError) && (
              <p role="alert" className="alert">
                {startError ?? formError}
              </p>
            )}
            <div className="actions">
              <button
                type="button"
                className="button button--secondary"
                disabled={submitting}
                onClick={() => {
                  setFieldErrors({})
                  setFormError(null)
                  setStep('slot')
                }}
              >
                Назад
              </button>
              <button type="submit" className="button" disabled={submitting}>
                Записаться
              </button>
            </div>
          </form>
        )}
      </div>
    </main>
  )
}
