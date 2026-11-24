import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from '../App'

const booking = {
  id: 'b1',
  eventType: { id: 'consultation', title: 'Консультация' },
  start: new Date('2026-11-23T10:00:00+05:00'),
  end: new Date('2026-11-23T11:30:00+05:00'),
  guestName: 'Анна',
  guestEmail: 'anna@example.com',
}

describe('Страница подтверждения', () => {
  it('показывает тип встречи, время в поясе владельца и данные гостя', () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: '/book/confirmation', state: { booking } }]}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Запись подтверждена' })).toBeInTheDocument()
    expect(screen.getByText('Консультация')).toBeInTheDocument()
    expect(screen.getByText(/23 ноября/)).toBeInTheDocument()
    expect(screen.getByText(/10:00/)).toBeInTheDocument()
    expect(screen.getByText(/11:30/)).toBeInTheDocument()
    expect(screen.getByText(/Asia\/Almaty/)).toBeInTheDocument()
    expect(screen.getByText(/Анна/)).toBeInTheDocument()
    expect(screen.getByText(/anna@example\.com/)).toBeInTheDocument()
  })

  it('без данных встречи (например, после перезагрузки) предлагает вернуться к записи', () => {
    render(
      <MemoryRouter initialEntries={['/book/confirmation']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByText('Данных о встрече нет.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'К записи' })).toHaveAttribute('href', '/book')
  })
})
