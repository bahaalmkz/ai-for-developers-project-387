package com.example.calendar.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Расчёт слотов с занятыми интервалами: задел под бронирование (занятое время не отдаётся). */
class SlotCalculatorTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
  private final SlotCalculator calculator =
      new SlotCalculator(
          new SlotSchedule(
              ZONE, LocalTime.of(9, 0), LocalTime.of(18, 0), Duration.ofMinutes(30), 14));

  private static Instant t(String iso) {
    return OffsetDateTime.parse(iso).toInstant();
  }

  @Test
  void busyIntervalRemovesEveryOverlappingSlotOfAnyDuration() {
    Instant now = t("2026-11-23T08:00:00+05:00");
    // занято 10:00–11:00
    List<Interval> busy =
        List.of(new Interval(t("2026-11-23T10:00:00+05:00"), t("2026-11-23T11:00:00+05:00")));

    List<Instant> starts =
        calculator.freeSlots(Duration.ofMinutes(60), now, busy).stream()
            .map(Interval::start)
            .filter(s -> s.isBefore(t("2026-11-24T00:00:00+05:00")))
            .toList();

    // 60-минутные слоты: 09:30–10:30, 10:00–11:00 и 10:30–11:30 пересекаются с занятым
    assertThat(starts).contains(t("2026-11-23T09:00:00+05:00"), t("2026-11-23T11:00:00+05:00"));
    assertThat(starts)
        .doesNotContain(
            t("2026-11-23T09:30:00+05:00"),
            t("2026-11-23T10:00:00+05:00"),
            t("2026-11-23T10:30:00+05:00"));
  }
}
