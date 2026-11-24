import { Link, NavLink, Route, Routes } from 'react-router-dom'
import './App.css'
import { BookingPage } from './pages/BookingPage'
import { ConfirmationPage } from './pages/ConfirmationPage'
import { HomePage } from './pages/HomePage'
import { OwnerPage } from './pages/OwnerPage'

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="15.5" rx="3" stroke="currentColor" strokeWidth="1.8" />
      <path d="M3.5 10h17M8 3v4M16 3v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="15.5" cy="15.5" r="2.2" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  )
}

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header__inner">
          <Link to="/" className="brand">
            <span className="brand__mark">
              <CalendarIcon />
            </span>
            Календарь
          </Link>
          {/* Без элементов списка: на главной тест считает ровно три пункта «Возможностей» */}
          <nav className="app-nav" aria-label="Разделы">
            <NavLink to="/book">Записаться</NavLink>
            <NavLink to="/owner">Владелец</NavLink>
          </nav>
        </div>
      </header>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/book" element={<BookingPage />} />
        <Route path="/book/confirmation" element={<ConfirmationPage />} />
        <Route path="/owner" element={<OwnerPage />} />
      </Routes>
    </div>
  )
}

export default App
