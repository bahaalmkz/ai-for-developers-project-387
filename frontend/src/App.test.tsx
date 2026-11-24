import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const indexHtmlPath = fileURLToPath(new URL('../index.html', import.meta.url))

beforeEach(() => {
  // Страница записи ходит в API: отдаём пустой список
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => new Response('[]', { headers: { 'Content-Type': 'application/json' } })),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('Главная страница', () => {
  it('рассказывает о сервисе и показывает три возможности', () => {
    renderAt('/')

    expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeInTheDocument()
    expect(screen.getByText(/запишитесь без регистрации/i)).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  it('ведёт на страницу записи', () => {
    renderAt('/')

    const main = screen.getByRole('main')
    expect(within(main).getByRole('link', { name: 'Записаться' })).toHaveAttribute('href', '/book')
  })

  it('в шапке ведёт на запись и в раздел владельца', () => {
    renderAt('/')

    const nav = screen.getByRole('navigation', { name: 'Разделы' })
    expect(within(nav).getByRole('link', { name: 'Записаться' })).toHaveAttribute('href', '/book')
    expect(within(nav).getByRole('link', { name: 'Владелец' })).toHaveAttribute('href', '/owner')
  })

  it('открывает страницу записи по клику на «Записаться»', async () => {
    renderAt('/')

    await userEvent.click(within(screen.getByRole('main')).getByRole('link', { name: 'Записаться' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Выберите тип встречи' })).toBeInTheDocument()
  })

  it('не показывает статус сервера', () => {
    renderAt('/')

    expect(screen.queryByText(/статус сервера/i)).not.toBeInTheDocument()
  })
})

describe('Страница записи', () => {
  it('открывается по адресу /book и возвращает на главную', async () => {
    renderAt('/book')

    expect(screen.getByRole('heading', { level: 1, name: 'Выберите тип встречи' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: 'На главную' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeInTheDocument()
  })
})

describe('Метаданные страницы', () => {
  // Регрессия находки аудита lighthouse:meta-description
  it('index.html содержит meta description с непустым content', () => {
    const html = readFileSync(indexHtmlPath, 'utf8')
    const metaTags = html.match(/<meta\b[^>]*>/gi) ?? []
    const descriptionTag = metaTags.find((tag) => /\bname\s*=\s*["']description["']/i.test(tag))

    expect(descriptionTag).toBeDefined()
    const content = descriptionTag?.match(/\bcontent\s*=\s*["']([^"']*)["']/i)?.[1] ?? ''
    expect(content.trim()).not.toBe('')
  })
})
