package com.example.calendar.booking;

import com.example.calendar.model.Booking;
import com.example.calendar.model.EventTypeRef;
import com.example.calendar.slot.BusyIntervals;
import com.example.calendar.slot.Interval;
import com.example.calendar.slot.SlotSchedule;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Хранилище встреч. Он же отдаёт занятые интервалы для расчёта слотов. */
@Repository
class BookingRepository implements BusyIntervals {

  private final JdbcClient jdbc;
  private final SlotSchedule schedule;

  BookingRepository(JdbcClient jdbc, SlotSchedule schedule) {
    this.jdbc = jdbc;
    this.schedule = schedule;
  }

  @Override
  public List<Interval> all() {
    return jdbc.sql("SELECT start_at, end_at FROM bookings")
        .query(
            (rs, rowNum) ->
                new Interval(
                    rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("end_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  /** Встречи, чей конец позже {@code now}, по возрастанию начала. */
  List<Booking> upcoming(Instant now) {
    return jdbc.sql(
            "SELECT b.id, b.event_type_id, t.title, b.start_at, b.end_at, b.guest_name,"
                + " b.guest_email, b.guest_topic FROM bookings b"
                + " JOIN event_types t ON t.id = b.event_type_id"
                + " WHERE b.end_at > :now ORDER BY b.start_at, b.id")
        .param("now", now.atOffset(ZoneOffset.UTC))
        .query(
            (rs, rowNum) ->
                new Booking()
                    .id(rs.getString("id"))
                    .eventType(
                        new EventTypeRef()
                            .id(rs.getString("event_type_id"))
                            .title(rs.getString("title")))
                    .start(inOwnerZone(rs.getObject("start_at", OffsetDateTime.class)))
                    .end(inOwnerZone(rs.getObject("end_at", OffsetDateTime.class)))
                    .guestName(rs.getString("guest_name"))
                    .guestEmail(rs.getString("guest_email"))
                    .guestTopic(rs.getString("guest_topic")))
        .list();
  }

  private OffsetDateTime inOwnerZone(OffsetDateTime value) {
    return value.toInstant().atZone(schedule.zone()).toOffsetDateTime();
  }

  Booking insert(
      EventTypeRef eventType,
      Interval interval,
      String guestName,
      String guestEmail,
      String guestTopic) {
    String id = UUID.randomUUID().toString();
    OffsetDateTime start = interval.start().atZone(schedule.zone()).toOffsetDateTime();
    OffsetDateTime end = interval.end().atZone(schedule.zone()).toOffsetDateTime();
    jdbc.sql(
            "INSERT INTO bookings"
                + " (id, event_type_id, start_at, end_at, guest_name, guest_email, guest_topic)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)")
        .params(id, eventType.getId(), start, end, guestName, guestEmail, guestTopic)
        .update();
    return new Booking()
        .id(id)
        .eventType(eventType)
        .start(start)
        .end(end)
        .guestName(guestName)
        .guestEmail(guestEmail)
        .guestTopic(guestTopic);
  }
}
