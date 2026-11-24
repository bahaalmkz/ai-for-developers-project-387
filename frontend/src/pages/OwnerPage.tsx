import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { bookingsApi, eventTypesApi } from '../api/client'
import { ResponseError, type ApiError, type Booking, type EventType } from '../api/generated'
import { dayFormat, timeFormat } from './time'

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; eventTypes: EventType[] }

type BookingsState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; bookings: Booking[] }

const emptyForm = { id: '', title: '', description: '', durationMinutes: '30' }

export function OwnerPage() {
  const [list, setList] = useState<ListState>({ status: 'loading' })
  const [bookings, setBookings] = useState<BookingsState>({ status: 'loading' })
  const [form, setForm] = useState(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    eventTypesApi
      .listEventTypes()
      .then((eventTypes) => {
        if (!cancelled) setList({ status: 'ready', eventTypes })
      })
      .catch(() => {
        if (!cancelled) setList({ status: 'error' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    bookingsApi
      .listBookings()
      .then((items) => {
        if (!cancelled) setBookings({ status: 'ready', bookings: items })
      })
      .catch(() => {
        if (!cancelled) setBookings({ status: 'error' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  function update(field: keyof typeof emptyForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    setFormError(null)
    try {
      const created = await eventTypesApi.createEventType({
        createEventTypeRequest: {
          id: form.id,
          title: form.title,
          description: form.description,
          durationMinutes: Number(form.durationMinutes),
        },
      })
      setList((current) => ({
        status: 'ready',
        eventTypes: current.status === 'ready' ? [...current.eventTypes, created] : [created],
      }))
      setForm(emptyForm)
    } catch (error) {
      if (error instanceof ResponseError) {
        const body = (await error.response.json().catch(() => null)) as ApiError | null
        if (error.response.status === 400 && body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
          setFieldErrors(body.fieldErrors)
          return
        }
        setFormError(body?.message ?? 'Не удалось создать тип встречи')
      } else {
        setFormError('Не удалось создать тип встречи')
      }
    } finally {
      setSubmitting(false)
    }
  }

  function field(name: keyof typeof emptyForm, label: string, type = 'text') {
    const error = fieldErrors[name]
    const errorId = `${name}-error`
    return (
      <div className="field">
        <label>
          {label}
          <input
            type={type}
            value={form[name]}
            onChange={(e) => update(name, e.target.value)}
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

  return (
    <main className="page">
      <header className="page-header">
        <h1>Раздел владельца</h1>
        <p className="lead">Типы встреч для записи и предстоящие встречи всех типов.</p>
      </header>
      <div className="owner-grid">
        <section className="card" aria-labelledby="new-event-type">
          <h2 id="new-event-type">Новый тип встречи</h2>
          <form onSubmit={submit} noValidate className="form">
            {field('id', 'Идентификатор')}
            {field('title', 'Название')}
            {field('description', 'Описание')}
            {field('durationMinutes', 'Длительность, мин', 'number')}
            {formError && (
              <p role="alert" className="alert">
                {formError}
              </p>
            )}
            <button type="submit" className="button" disabled={submitting}>
              Создать
            </button>
          </form>
        </section>

        <div className="owner-lists">
          <section className="card" aria-labelledby="upcoming-bookings">
            <h2 id="upcoming-bookings">Предстоящие встречи</h2>
            {bookings.status === 'loading' && <p className="muted">Загрузка…</p>}
            {bookings.status === 'error' && (
              <p role="alert" className="alert">
                Не удалось загрузить встречи. Попробуйте обновить страницу.
              </p>
            )}
            {bookings.status === 'ready' && bookings.bookings.length === 0 && (
              <p className="muted">Предстоящих встреч пока нет.</p>
            )}
            {bookings.status === 'ready' && bookings.bookings.length > 0 && (
              <ul aria-label="Предстоящие встречи" className="item-list">
                {bookings.bookings.map((booking) => (
                  <li key={booking.id}>
                    <span>
                      <strong>{booking.eventType.title}</strong>: {dayFormat.format(booking.start)},{' '}
                      {timeFormat.format(booking.start)}–{timeFormat.format(booking.end)}
                    </span>
                    <p className="muted">
                      {booking.guestName}, {booking.guestEmail}
                    </p>
                    {booking.guestTopic && <p>Тема: {booking.guestTopic}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card" aria-labelledby="event-types">
            <h2 id="event-types">Созданные типы встреч</h2>
            {list.status === 'loading' && <p className="muted">Загрузка…</p>}
            {list.status === 'error' && (
              <p role="alert" className="alert">
                Не удалось загрузить типы встреч. Попробуйте обновить страницу.
              </p>
            )}
            {list.status === 'ready' && list.eventTypes.length === 0 && (
              <p className="muted">Типов встреч пока нет.</p>
            )}
            {list.status === 'ready' && list.eventTypes.length > 0 && (
              <ul aria-label="Типы встреч" className="item-list">
                {list.eventTypes.map((eventType) => (
                  <li key={eventType.id}>
                    <span>
                      <strong>{eventType.title}</strong> ({eventType.id}), {eventType.durationMinutes}{' '}
                      мин
                    </span>
                    {eventType.description && <p className="muted">{eventType.description}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
      <Link to="/" className="back-link">
        На главную
      </Link>
    </main>
  )
}
