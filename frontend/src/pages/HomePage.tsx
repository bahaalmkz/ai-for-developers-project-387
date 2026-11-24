import { Link } from 'react-router-dom'

export function HomePage() {
  return (
    <main className="home">
      <div className="page hero">
        <div className="hero__text">
          <span className="eyebrow">Быстрая запись на звонок</span>
          <h1>Календарь звонков</h1>
          <p className="lead">
            Выберите тип встречи, найдите свободное время и запишитесь без регистрации.
          </p>
          <Link to="/book" className="button button--large">
            Записаться
            <span aria-hidden="true">→</span>
          </Link>
        </div>
        <section className="card features" aria-labelledby="features-title">
          <h2 id="features-title">Возможности</h2>
          <ul className="features__list">
            <li>Выбор типа встречи и удобного времени в ближайшие 14 дней.</li>
            <li>Запись без регистрации: имя, email и подтверждение.</li>
            <li>Управление типами встреч и список предстоящих встреч у владельца.</li>
          </ul>
        </section>
      </div>
    </main>
  )
}
