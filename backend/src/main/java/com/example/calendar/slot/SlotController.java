package com.example.calendar.slot;

import com.example.calendar.api.SlotsApi;
import com.example.calendar.model.Slot;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.web.bind.annotation.RestController
class SlotController implements SlotsApi {

  private final JdbcClient jdbc;
  private final Clock clock;
  private final SlotCalculator calculator;
  private final SlotSchedule schedule;

  private final BusyIntervals busy;

  SlotController(
      JdbcClient jdbc,
      Clock clock,
      SlotCalculator calculator,
      SlotSchedule schedule,
      BusyIntervals busy) {
    this.jdbc = jdbc;
    this.busy = busy;
    this.clock = clock;
    this.calculator = calculator;
    this.schedule = schedule;
  }

  @Override
  public ResponseEntity<List<Slot>> listSlots(String id) {
    Integer minutes =
        jdbc.sql("SELECT duration_minutes FROM event_types WHERE id = :id")
            .param("id", id)
            .query(Integer.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    List<Slot> slots =
        calculator.freeSlots(Duration.ofMinutes(minutes), clock.instant(), busy.all()).stream()
            .map(
                interval ->
                    new Slot()
                        .start(interval.start().atZone(schedule.zone()).toOffsetDateTime())
                        .end(interval.end().atZone(schedule.zone()).toOffsetDateTime()))
            .toList();
    return ResponseEntity.ok(slots);
  }
}
