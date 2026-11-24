import { expect, test, type Page } from '@playwright/test'

const eventType = { id: 'e2e-call', title: 'Сценарий E2E', description: 'Проверка записи' }

// Время выбранного дня: кнопки слотов названы временем начала («09:00»)
function daySlots(page: Page) {
  return page
    .getByRole('region', { name: 'Свободные слоты' })
    .getByRole('button', { name: /^\d\d:\d\d$/ })
}

// Берём второй день со свободным временем: первый слот сегодняшнего дня может уйти в прошлое посреди теста
async function openSecondAvailableDay(page: Page) {
  const days = page.getByRole('group', { name: 'Календарь' }).getByRole('button', { name: /свободно: \d+/ })
  await expect(days.first()).toBeVisible()
  if ((await days.count()) >= 2) {
    await days.nth(1).click()
  } else {
    // Второй день попал в следующий месяц
    await page.getByRole('button', { name: 'Следующий месяц' }).click()
    await days.first().click()
  }
}

async function chooseSecondDayFirstSlot(page: Page) {
  await page.goto('/book')
  await page.getByRole('button', { name: `Выбрать ${eventType.title}` }).click()
  await openSecondAvailableDay(page)
  const firstSlot = daySlots(page).first()
  await firstSlot.click()
  const slotTime = (await firstSlot.getAttribute('aria-label'))!
  await page.getByRole('button', { name: 'Продолжить' }).click()
  return slotTime
}

async function fillGuest(page: Page, name: string, email: string) {
  const form = page.getByRole('form', { name: 'Данные гостя' })
  await form.getByLabel('Имя').fill(name)
  await form.getByLabel('Email').fill(email)
  return form
}

test('владелец создаёт тип встречи, гость записывается, повторная запись даёт конфликт', async ({
  page,
  browser,
}) => {
  // Владелец создаёт тип встречи
  await page.goto('/owner')
  await page.getByLabel('Идентификатор').fill(eventType.id)
  await page.getByLabel('Название').fill(eventType.title)
  await page.getByLabel('Описание').fill(eventType.description)
  await page.getByLabel('Длительность, мин').fill('30')
  await page.getByRole('button', { name: 'Создать' }).click()
  await expect(page.getByRole('list', { name: 'Типы встреч' })).toContainText(eventType.title)

  // Второй гость открывает слоты заранее и держит выбранное время
  const lateContext = await browser.newContext()
  const latePage = await lateContext.newPage()
  const slotTime = await chooseSecondDayFirstSlot(latePage)
  const lateForm = await fillGuest(latePage, 'Опоздавший Гость', 'late@example.com')

  // Первый гость записывается на то же время и видит подтверждение
  expect(await chooseSecondDayFirstSlot(page)).toBe(slotTime)
  const form = await fillGuest(page, 'Анна Гость', 'anna@example.com')
  await form.getByRole('button', { name: 'Записаться' }).click()
  await expect(page.getByRole('heading', { name: 'Запись подтверждена' })).toBeVisible()
  await expect(page.getByText(eventType.title)).toBeVisible()
  await expect(page.getByText(slotTime, { exact: false }).first()).toBeVisible()

  // Встреча видна владельцу в списке предстоящих
  await page.goto('/owner')
  const upcoming = page.getByRole('list', { name: 'Предстоящие встречи' })
  await expect(upcoming).toContainText(eventType.title)
  await expect(upcoming).toContainText('Анна Гость')
  await expect(upcoming).toContainText('anna@example.com')

  // Второй гость пытается занять то же время и видит конфликт
  await lateForm.getByRole('button', { name: 'Записаться' }).click()
  await expect(latePage.getByRole('alert')).toHaveText('Это время уже занято, выберите другой слот')
  // Гость снова на выборе времени того же дня: занятое время из списка пропало
  await expect(daySlots(latePage).first()).not.toHaveAttribute('aria-label', slotTime)

  await lateContext.close()
})
