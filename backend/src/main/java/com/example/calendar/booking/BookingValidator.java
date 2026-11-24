package com.example.calendar.booking;

import com.example.calendar.error.ValidationException;
import com.example.calendar.model.CreateBookingRequest;
import com.example.calendar.slot.SlotSchedule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Правила данных записи (спецификация #13): имя, email и допустимый старт. Занятость времени здесь
 * не проверяется: это отдельная транзакционная проверка в {@code BookingService}.
 */
final class BookingValidator {

  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

  static final int MAX_TOPIC_LENGTH = 200;

  private final SlotSchedule schedule;

  BookingValidator(SlotSchedule schedule) {
    this.schedule = schedule;
  }

  /** Тема разговора в сохраняемом виде: без пробелов по краям, пустая строка — темы нет. */
  static String topicOf(CreateBookingRequest request) {
    String topic = request.getGuestTopic();
    return topic == null ? "" : topic.strip();
  }

  void validate(CreateBookingRequest request, Duration duration, Instant now) {
    Map<String, String> errors = new LinkedHashMap<>();

    String name = request.getGuestName();
    if (name == null || name.isBlank()) {
      errors.put("guestName", "Введите имя");
    } else if (name.length() > 100) {
      errors.put("guestName", "Имя не длиннее 100 символов");
    }

    String email = request.getGuestEmail();
    if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
      errors.put("guestEmail", "Введите email в формате name@example.com");
    }

    // Тема необязательна: null и одни пробелы — темы нет. Длина — после обрезки пробелов по краям
    if (topicOf(request).length() > MAX_TOPIC_LENGTH) {
      errors.put("guestTopic", "Тема не длиннее " + MAX_TOPIC_LENGTH + " символов");
    }

    String startError = startError(request, duration, now);
    if (startError != null) {
      errors.put("start", startError);
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private String startError(CreateBookingRequest request, Duration duration, Instant now) {
    if (request.getStart() == null) {
      return "Укажите время начала";
    }
    ZonedDateTime start = request.getStart().atZoneSameInstant(schedule.zone());
    LocalTime time = start.toLocalTime();
    long minutesFromOpen = Duration.between(schedule.workStart(), time).toMinutes();
    boolean onGrid =
        start.getSecond() == 0
            && start.getNano() == 0
            && !time.isBefore(schedule.workStart())
            && minutesFromOpen % schedule.step().toMinutes() == 0;
    if (!onGrid) {
      return "Начало должно быть на сетке " + schedule.step().toMinutes() + " минут в рабочие часы";
    }
    LocalDate today = now.atZone(schedule.zone()).toLocalDate();
    LocalDate date = start.toLocalDate();
    if (date.isBefore(today) || !date.isBefore(today.plusDays(schedule.windowDays()))) {
      return "Время вне окна записи (" + schedule.windowDays() + " дней)";
    }
    if (!start.toInstant().isAfter(now)) {
      return "Это время уже прошло";
    }
    LocalTime end = time.plus(duration);
    if (end.isBefore(time) || end.isAfter(schedule.workEnd())) {
      return "Встреча должна закончиться до " + schedule.workEnd();
    }
    return null;
  }
}
