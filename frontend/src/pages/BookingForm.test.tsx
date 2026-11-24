import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const eventTypes = [{ id: 'intro-call', title: 'Знакомство', description: '', durationMinutes: 30 }]
const slots = [
  { start: '2026-11-23T09:00:00+05:00', end: '2026-11-23T09:30:00+05:00' },
  { start: '2026-11-23T09:30:00+05:00', end: '2026-11-23T10:00:00+05:00' },
]

type Handler = (init: RequestInit) => Response

// Подменяем сеть: список типов, слоты и POST /api/bookings
function stubApi(onBooking: Handler) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/bookings' && init?.method === 'POST') return onBooking(init)
    if (url === '/api/event-types') return json(eventTypes)
    if (url === '/api/event-types/intro-call/slots') return json(slots)
    throw new Error(`Неожиданный запрос ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function bookingCalls(fetchMock: ReturnType<typeof stubApi>) {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/bookings')
}

async function openFormForSecondSlot() {
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
  return user
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Форма гостя', () => {
  it('не показывается, пока слот не выбран', async () => {
    stubApi(() => json({}, 500))
    render(
      <MemoryRouter initialEntries={['/book']}>
        <App />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Выбрать Знакомство' }))
    await screen.findByRole('group', { name: /23 ноября/ })

    expect(screen.queryByLabelText('Имя')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Продолжить' })).toBeDisabled()
  })

  it('после выбора слота показывает форму и отправляет запись с выбранным слотом', async () => {
    const booking = {
      id: 'b1',
      eventType: { id: 'intro-call', title: 'Знакомство' },
      start: '2026-11-23T09:30:00+05:00',
      end: '2026-11-23T10:00:00+05:00',
      guestName: 'Анна',
      guestEmail: 'anna@example.com',
    }
    const fetchMock = stubApi(() => json(booking, 201))
    const user = await openFormForSecondSlot()

    await user.type(screen.getByLabelText('Имя'), 'Анна')
    await user.type(screen.getByLabelText('Email'), 'anna@example.com')
    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    // Переход на подтверждение с данными встречи
    expect(await screen.findByRole('heading', { name: 'Запись подтверждена' })).toBeInTheDocument()
    expect(screen.getByText(/Знакомство/)).toBeInTheDocument()
    expect(screen.getByText(/09:30/)).toBeInTheDocument()
    const [, init] = bookingCalls(fetchMock)[0]
    expect(JSON.parse(init?.body as string)).toEqual({
      eventTypeId: 'intro-call',
      start: '2026-11-23T04:30:00.000Z',
      guestName: 'Анна',
      guestEmail: 'anna@example.com',
    })
  })

  it('показывает ошибки по полям из ответа сервера и остаётся на странице', async () => {
    stubApi(() =>
      json(
        {
          code: 'validation_error',
          message: 'Ошибка данных',
          fieldErrors: { guestName: 'Введите имя', guestEmail: 'Введите email в формате' },
        },
        400,
      ),
    )
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    const name = await screen.findByLabelText('Имя')
    expect(name).toHaveAccessibleDescription('Введите имя')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(/Введите email/)
    expect(screen.queryByRole('heading', { name: 'Запись подтверждена' })).not.toBeInTheDocument()
  })

  it('показывает ошибку старта у формы, а не у поля ввода', async () => {
    stubApi(() =>
      json(
        { code: 'validation_error', message: 'Ошибка данных', fieldErrors: { start: 'Это время уже прошло' } },
        400,
      ),
    )
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Это время уже прошло')
  })

  it('показывает сообщение сервера, если запись не удалась', async () => {
    stubApi(() => json({ code: 'not_found', message: 'Not Found' }, 404))
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Not Found')
  })

  it('при 409 показывает сообщение сервера и обновляет слоты без занятого', async () => {
    let slotRequests = 0
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/bookings' && init?.method === 'POST') {
        return json({ code: 'conflict', message: 'Это время уже занято, выберите другой слот' }, 409)
      }
      if (url === '/api/event-types') return json(eventTypes)
      if (url === '/api/event-types/intro-call/slots') {
        slotRequests += 1
        // после конфликта слот 09:30 уже занят
        return json(slotRequests === 1 ? slots : slots.slice(0, 1))
      }
      throw new Error(`Неожиданный запрос ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Это время уже занято, выберите другой слот',
    )
    const day = await screen.findByRole('group', { name: /23 ноября/ })
    await vi.waitFor(() =>
      expect(within(day).queryByRole('button', { name: '09:30' })).not.toBeInTheDocument(),
    )
    expect(within(day).getByRole('button', { name: '09:00' })).toBeInTheDocument()
    expect(slotRequests).toBe(2)
    expect(screen.queryByRole('heading', { name: 'Запись подтверждена' })).not.toBeInTheDocument()
  })

  it('при 400 без fieldErrors показывает общее сообщение', async () => {
    stubApi(() => json({ code: 'validation_error', message: 'Не удалось разобрать запрос' }, 400))
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось разобрать запрос')
  })

  it('при 400 с пустым fieldErrors показывает общее сообщение', async () => {
    stubApi(() =>
      json({ code: 'validation_error', message: 'Ошибка данных', fieldErrors: {} }, 400),
    )
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ошибка данных')
  })

  it('отбрасывает запоздалый ответ слотов прежнего типа встречи', async () => {
    const types = [
      { id: 'a', title: 'Первый', description: '', durationMinutes: 30 },
      { id: 'b', title: 'Второй', description: '', durationMinutes: 30 },
    ]
    let resolveA: (response: Response) => void = () => {}
    const slowA = new Promise<Response>((resolve) => {
      resolveA = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/event-types') return json(types)
        if (url === '/api/event-types/a/slots') return slowA
        if (url === '/api/event-types/b/slots')
          return json([{ start: '2026-11-23T11:00:00+05:00', end: '2026-11-23T11:30:00+05:00' }])
        throw new Error(`Неожиданный запрос ${url}`)
      }),
    )
    render(
      <MemoryRouter initialEntries={['/book']}>
        <App />
      </MemoryRouter>,
    )
    const user = userEvent.setup()
    // Слоты первого типа ещё не пришли, а гость уже вернулся и выбрал второй тип
    await user.click(await screen.findByRole('button', { name: 'Выбрать Первый' }))
    await user.click(screen.getByRole('button', { name: 'Назад' }))
    await user.click(screen.getByRole('button', { name: 'Выбрать Второй' }))
    expect(await screen.findByRole('button', { name: '11:00' })).toBeInTheDocument()

    resolveA(json([{ start: '2026-11-23T09:00:00+05:00', end: '2026-11-23T09:30:00+05:00' }]))
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(screen.queryByRole('button', { name: '09:00' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '11:00' })).toBeInTheDocument()
  })

  it('на время отправки блокирует возврат к выбору времени и повторную отправку', async () => {
    let resolveBooking: (response: Response) => void = () => {}
    const pending = new Promise<Response>((resolve) => {
      resolveBooking = resolve
    })
    const fetchMock = stubApi(() => pending as unknown as Response)
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Записаться' }))

    expect(screen.getByRole('button', { name: 'Записаться' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled()
    expect(bookingCalls(fetchMock)).toHaveLength(1)

    resolveBooking(json({}, 500))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Назад' })).toBeEnabled()
  })

  it('«Назад» в форме возвращает к выбору времени с сохранённым выбором', async () => {
    stubApi(() => json({}, 500))
    const user = await openFormForSecondSlot()

    await user.click(screen.getByRole('button', { name: 'Назад' }))

    const day = await screen.findByRole('group', { name: /23 ноября/ })
    expect(within(day).getByRole('button', { name: '09:30' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByLabelText('Имя')).not.toBeInTheDocument()
  })
})
