import { Link, useLocation } from 'react-router-dom'
import type { Booking } from '../api/generated'
import { OWNER_TIMEZONE, dayFormat, timeFormat } from './time'

// Данные встречи приходят только через состояние перехода и не сохраняются при перезагрузке
export function ConfirmationPage() {
  const location = useLocation()
  const booking = (location.state as { booking?: Booking } | null)?.booking

  if (!booking) {
    return (
      <main className="page">
        <div className="card confirmation">
          <h1>Подтверждение</h1>
          <p className="muted">Данных о встрече нет.</p>
          <Link to="/book" className="button">
            К записи
          </Link>
        </div>
      </main>
    )
  }

  return (
    <main className="page">
      <div className="card confirmation">
        <span className="confirmation__icon" aria-hidden="true">
          ✓
        </span>
        <h1>Запись подтверждена</h1>
        <div className="confirmation__details">
          <p>
            <strong>{booking.eventType.title}</strong>
          </p>
          <p>
            {dayFormat.format(booking.start)}, {timeFormat.format(booking.start)}–
            {timeFormat.format(booking.end)}
          </p>
          <p className="muted">
            Время указано в часовом поясе владельца: {OWNER_TIMEZONE} (UTC+5).
          </p>
          <p>
            Гость: {booking.guestName}, {booking.guestEmail}
          </p>
        </div>
        <Link to="/" className="back-link">
          На главную
        </Link>
      </div>
    </main>
  )
}
