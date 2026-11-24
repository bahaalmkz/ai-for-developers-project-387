package com.example.calendar.booking;

import com.example.calendar.error.ConflictException;
import com.example.calendar.model.Booking;
import com.example.calendar.model.CreateBookingRequest;
import com.example.calendar.model.EventTypeRef;
import com.example.calendar.slot.Interval;
import com.example.calendar.slot.SlotSchedule;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Запись гостя на слот: тип встречи, проверка данных, проверка занятости, вставка. */
@Service
class BookingService {

  private record TypeRow(String title, int minutes) {}

  private final JdbcClient jdbc;
  private final Clock clock;
  private final BookingRepository bookings;
  private final BookingValidator validator;

  BookingService(JdbcClient jdbc, Clock clock, BookingRepository bookings, SlotSchedule schedule) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.bookings = bookings;
    this.validator = new BookingValidator(schedule);
  }

  @Transactional
  Booking create(CreateBookingRequest request) {
    TypeRow type =
        jdbc.sql("SELECT title, duration_minutes FROM event_types WHERE id = :id")
            .param("id", String.valueOf(request.getEventTypeId()))
            .query(
                (rs, rowNum) -> new TypeRow(rs.getString("title"), rs.getInt("duration_minutes")))
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    Duration duration = Duration.ofMinutes(type.minutes());

    validator.validate(request, duration, clock.instant());

    // Конец встречи считает сервер: старт + длительность типа
    Interval interval =
        new Interval(request.getStart().toInstant(), request.getStart().toInstant().plus(duration));
    ensureIntervalIsFree(interval);
    return bookings.insert(
        new EventTypeRef().id(request.getEventTypeId()).title(type.title()),
        interval,
        request.getGuestName().trim(),
        request.getGuestEmail(),
        BookingValidator.topicOf(request));
  }

  /**
   * Проверка пересечений с занятым временем (полуоткрытые интервалы, занятость общая для всех
   * типов). Защита от гонки: сначала блокируем единственную строку calendar_lock (SELECT ... FOR
   * UPDATE) на время транзакции, затем проверяем и вставляем. Второй запрос ждёт коммита первого и
   * видит его встречу. Ограничение уникальности тут не подходит: пересечение интервалов им не
   * выразить, а блокировка на уровне БД работает и при нескольких экземплярах сервера.
   */
  private void ensureIntervalIsFree(Interval interval) {
    jdbc.sql("SELECT id FROM calendar_lock WHERE id = 1 FOR UPDATE").query().listOfRows();
    if (bookings.all().stream().anyMatch(interval::overlaps)) {
      throw new ConflictException("Это время уже занято, выберите другой слот");
    }
  }
}
