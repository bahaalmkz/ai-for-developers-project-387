import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { OwnerPage } from './OwnerPage'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Handler = (init: RequestInit | undefined) => Response

// Подменяем сеть: GET/POST /api/event-types и GET /api/bookings
function stubApi(initial: unknown[], onPost: Handler, bookings: unknown[] = []) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/event-types' && init?.method === 'POST') return onPost(init)
    if (url === '/api/event-types') return json(initial)
    if (url === '/api/bookings') return json(bookings)
    throw new Error(`Неожиданный запрос ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderPage() {
  return render(
    <MemoryRouter>
      <OwnerPage />
    </MemoryRouter>,
  )
}

async function fillForm(values: { id?: string; title?: string; description?: string; duration?: string }) {
  const user = userEvent.setup()
  if (values.id !== undefined) await user.type(screen.getByLabelText('Идентификатор'), values.id)
  if (values.title !== undefined) await user.type(screen.getByLabelText('Название'), values.title)
  if (values.description !== undefined)
    await user.type(screen.getByLabelText('Описание'), values.description)
  if (values.duration !== undefined) {
    await user.clear(screen.getByLabelText('Длительность, мин'))
    await user.type(screen.getByLabelText('Длительность, мин'), values.duration)
  }
  await user.click(screen.getByRole('button', { name: 'Создать' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Раздел владельца: создание типа встречи', () => {
  it('показывает уже созданные типы встреч', async () => {
    stubApi(
      [{ id: 'intro', title: 'Знакомство', description: '', durationMinutes: 30 }],
      () => json({}, 500),
    )

    renderPage()

    const list = await screen.findByRole('list', { name: 'Типы встреч' })
    expect(within(list).getByText('Знакомство')).toBeInTheDocument()
  })

  it('отправляет форму и добавляет созданный тип в список', async () => {
    const created = { id: 'deep-dive', title: 'Разбор', description: 'Подробно', durationMinutes: 90 }
    const fetchMock = stubApi([], () => json(created, 201))
    renderPage()
    await screen.findByText('Типов встреч пока нет.')

    await fillForm({ id: 'deep-dive', title: 'Разбор', description: 'Подробно', duration: '90' })

    const list = await screen.findByRole('list', { name: 'Типы встреч' })
    expect(within(list).getByText('Разбор')).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toEqual({
      id: 'deep-dive',
      title: 'Разбор',
      description: 'Подробно',
      durationMinutes: 90,
    })
  })

  it('показывает ошибки рядом с полями при 400', async () => {
    stubApi([], () =>
      json(
        {
          code: 'validation_error',
          message: 'Ошибка данных',
          fieldErrors: {
            id: 'Только латиница в нижнем регистре, цифры и дефис',
            durationMinutes: 'Длительность кратна 30, от 30 до 480 минут',
          },
        },
        400,
      ),
    )
    renderPage()
    await screen.findByText('Типов встреч пока нет.')

    await fillForm({ id: 'BAD', title: 'Название', duration: '45' })

    expect(await screen.findByText('Только латиница в нижнем регистре, цифры и дефис')).toBeInTheDocument()
    expect(screen.getByLabelText('Идентификатор')).toBeInvalid()
    expect(screen.getByText('Длительность кратна 30, от 30 до 480 минут')).toBeInTheDocument()
    expect(screen.getByLabelText('Название')).toBeValid()
    expect(screen.queryByRole('list', { name: 'Типы встреч' })).not.toBeInTheDocument()
  })

  it('показывает сообщение о дубликате при 409', async () => {
    stubApi([], () =>
      json({ code: 'conflict', message: 'Тип встречи с id «intro» уже существует' }, 409),
    )
    renderPage()
    await screen.findByText('Типов встреч пока нет.')

    await fillForm({ id: 'intro', title: 'Знакомство' })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Тип встречи с id «intro» уже существует',
    )
  })

  it('при 400 с пустым fieldErrors показывает общее сообщение', async () => {
    stubApi([], () =>
      json({ code: 'validation_error', message: 'Ошибка данных', fieldErrors: {} }, 400),
    )
    renderPage()
    await screen.findByText('Типов встреч пока нет.')

    await fillForm({ id: 'a', title: 'Название', duration: '30' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Ошибка данных')
  })

  it('при 400 без fieldErrors показывает общее сообщение', async () => {
    stubApi([], () => json({ code: 'validation_error', message: 'Не удалось разобрать запрос' }, 400))
    renderPage()
    await screen.findByText('Типов встреч пока нет.')

    await fillForm({ id: 'a', title: 'Название', duration: '30' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось разобрать запрос')
  })
})

describe('Раздел владельца: предстоящие встречи', () => {
  const booking = (id: string, title: string, guest: string, start: string, end: string) => ({
    id,
    eventType: { id: 'x', title },
    start,
    end,
    guestName: guest,
    guestEmail: `${id}@example.com`,
  })

  it('показывает пустое состояние, когда встреч нет', async () => {
    stubApi([], () => json({}, 500), [])

    renderPage()

    expect(await screen.findByText('Предстоящих встреч пока нет.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Предстоящие встречи' })).not.toBeInTheDocument()
  })

  it('показывает встречи в порядке ответа: тип, время, имя и email гостя', async () => {
    stubApi([], () => json({}, 500), [
      booking('anna', 'Консультация', 'Анна', '2026-11-23T10:00:00+05:00', '2026-11-23T11:30:00+05:00'),
      booking('boris', 'Знакомство', 'Борис', '2026-11-24T09:00:00+05:00', '2026-11-24T09:30:00+05:00'),
    ])

    renderPage()

    const list = await screen.findByRole('list', { name: 'Предстоящие встречи' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Консультация')
    expect(items[0]).toHaveTextContent('10:00')
    expect(items[0]).toHaveTextContent('11:30')
    expect(items[0]).toHaveTextContent('Анна')
    expect(items[0]).toHaveTextContent('anna@example.com')
    expect(items[1]).toHaveTextContent('Знакомство')
    expect(items[1]).toHaveTextContent('Борис')
  })

  it('показывает ошибку, если встречи не загрузились', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/event-types') return json([])
        return json({ code: 'internal_error', message: 'Ошибка' }, 500)
      }),
    )

    renderPage()

    expect(
      await screen.findByText('Не удалось загрузить встречи. Попробуйте обновить страницу.'),
    ).toBeInTheDocument()
  })
})
