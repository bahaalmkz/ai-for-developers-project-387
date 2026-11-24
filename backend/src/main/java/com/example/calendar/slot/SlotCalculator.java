package com.example.calendar.slot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Расчёт свободных слотов: чистая функция от расписания, длительности, текущего момента и занятых
 * интервалов. Не знает ни про БД, ни про HTTP.
 */
public class SlotCalculator {

  private final SlotSchedule schedule;

  public SlotCalculator(SlotSchedule schedule) {
    this.schedule = schedule;
  }

  /** Свободные слоты на окно записи по возрастанию старта. */
  public List<Interval> freeSlots(Duration duration, Instant now, List<Interval> busy) {
    LocalDate today = now.atZone(schedule.zone()).toLocalDate();
    List<Interval> slots = new ArrayList<>();
    for (int day = 0; day < schedule.windowDays(); day++) {
      LocalDate date = today.plusDays(day);
      ZonedDateTime close = date.atTime(schedule.workEnd()).atZone(schedule.zone());
      ZonedDateTime start = date.atTime(schedule.workStart()).atZone(schedule.zone());
      while (!start.plus(duration).isAfter(close)) {
        Interval slot = new Interval(start.toInstant(), start.plus(duration).toInstant());
        if (slot.start().isAfter(now) && busy.stream().noneMatch(slot::overlaps)) {
          slots.add(slot);
        }
        start = start.plus(schedule.step());
      }
    }
    return slots;
  }
}
