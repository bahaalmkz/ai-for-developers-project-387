import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { OwnerPage } from './OwnerPage'

// Тема разговора (спецификация #8): форма гостя, подтверждение и список встреч владельца

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const eventTypes = [{ id: 'intro-call', title: 'Знакомство', description: '', durationMinutes: 30 }]
const slots = [{ start: '2026-11-23T09:30:00+05:00', end: '2026-11-23T10:00:00+05:00' }]

function bookingResponse(guestTopic: string) {
  return {
    id: 'b1',
    eventType: { id: 'intro-call', title: 'Знакомство' },
    start: '2026-11-23T09:30:00+05:00',
    end: '2026-11-23T10:00:00+05:00',
    guestName: 'Анна',
    guestEmail: 'anna@example.com',
    guestTopic,
  }
}

function stubBookingApi(onBooking: (init: RequestInit) => Response) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/bookings' && init?.method === 'POST') return onBooking(init)
    if (url === '/api/event-types') return json(eventTypes)
    if (url === '/api/event-types/intro-call/slots') return json(slots)
    throw new Error(`Неожиданный запрос ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function sentBody(fetchMock: ReturnType<typeof stubBookingApi>) {
  const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/bookings') ?? []
  return JSON.parse(init?.body as string) as Record<string, unknown>
}

async function openGuestForm() {
  render(
    <MemoryRouter initialEntries={['/book']}>
      <App />
    </MemoryRouter>,
  )
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Выбрать Знакомство' }))
  const day = await screen.findByRole('group', { name: /23 ноября/ })
  await user.click(within(day).getByRole('button', { name: '09:30' }))
  await user.click(screen.getByRole('button', { name: 'Продолжить' }))
  await user.type(screen.getByLabelText('Имя'), 'Анна')
  await user.type(screen.getByLabelText('Email'), 'anna@example.com')
  return user
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Тема разговора в записи гостя', () => {
  it('форма показывает необязательное поле темы с подсказкой о видимости', async () => {
    stubBookingApi(() => json({}, 500))
    await openGuestForm()

    const topic = screen.getByLabelText(/О чём хотите поговорить\?/)
    expect(topic).not.toBeRequired()
    expect(topic).toHaveAccessibleDescription(/не пишите конфиденциальное/i)
  })

  it('не даёт ввести в поле темы больше 200 символов', async () => {
    stubBookingApi(() => json({}, 500))
    const user = await openGuestForm()
    const topic = screen.getByLabelText(/О чём хотите поговорить\?/)

    await user.click(topic)
    await user.paste('т'.repeat(205))

    expect(topic).toHaveAttribute('maxLength', '200')
    expect(topic).toHaveValue('т'.repeat(200))
  })

  it('отправляет обрезанную тему и показывает её на подтверждении', async () => {
    const fetchMock = stubBookingApi(() => json(bookingResponse('Обсудить тариф'), 201))
    const user = await openGuestForm()

    await user.type(screen.getByLabelText(/О чём хотите поговорить\?/), '  Обсудить тариф  ')
    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('heading', { name: 'Запись подтверждена' })).toBeInTheDocument()
    expect(screen.getByText(/Тема: Обсудить тариф/)).toBeInTheDocument()
    expect(sentBody(fetchMock).guestTopic).toBe('Обсудить тариф')
  })

  it('показывает ошибку темы от сервера рядом с полем и остаётся на форме', async () => {
    stubBookingApi(() =>
      json(
        {
          code: 'validation_error',
          message: 'Ошибка данных',
          fieldErrors: { guestTopic: 'Тема не длиннее 200 символов' },
        },
        400,
      ),
    )
    const user = await openGuestForm()

    await user.type(screen.getByLabelText(/О чём хотите поговорить\?/), 'Слишком длинная тема')
    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByLabelText(/О чём хотите поговорить\?/)).toHaveAccessibleDescription(
      /Тема не длиннее 200 символов/,
    )
    expect(screen.queryByRole('heading', { name: 'Запись подтверждена' })).not.toBeInTheDocument()
  })

  it('без темы отправляет те же данные, что и раньше, и не показывает строку темы', async () => {
    const fetchMock = stubBookingApi(() => json(bookingResponse(''), 201))
    const user = await openGuestForm()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('heading', { name: 'Запись подтверждена' })).toBeInTheDocument()
    expect(screen.queryByText(/Тема:/)).not.toBeInTheDocument()
    expect(sentBody(fetchMock)).not.toHaveProperty('guestTopic')
  })
})

describe('Тема разговора в списке встреч владельца', () => {
  it('показывает тему только у встреч, где гость её указал', async () => {
    const withTopic = { ...bookingResponse('Обсудить тариф'), id: 'b1' }
    const withoutTopic = {
      ...bookingResponse(''),
      id: 'b2',
      guestName: 'Борис',
      guestEmail: 'boris@example.com',
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/event-types') return json([])
        if (url === '/api/bookings') return json([withTopic, withoutTopic])
        throw new Error(`Неожиданный запрос ${url}`)
      }),
    )
    render(
      <MemoryRouter>
        <OwnerPage />
      </MemoryRouter>,
    )

    const list = await screen.findByRole('list', { name: 'Предстоящие встречи' })
    const [first, second] = within(list).getAllByRole('listitem')
    expect(within(first).getByText(/Тема: Обсудить тариф/)).toBeInTheDocument()
    expect(within(second).getByText(/Борис/)).toBeInTheDocument()
    expect(within(second).queryByText(/Тема:/)).not.toBeInTheDocument()
    expect(within(second).queryByText('—')).not.toBeInTheDocument()
  })
})
