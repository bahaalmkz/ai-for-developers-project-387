package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** API-тест слотов: реальный HTTP, чистая БД, подменяемые часы. Часовой пояс расписания: +05:00. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(SlotsApiTest.TestClockConfig.class)
class SlotsApiTest {

  private static final AtomicReference<Instant> NOW = new AtomicReference<>();

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return NOW.get();
        }
      };
    }
  }

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM event_types");
    addEventType("intro-call", 30);
    addEventType("consultation", 90);
    // 2026-11-23 08:00 в Asia/Almaty (понедельник)
    at("2026-11-23T08:00:00+05:00");
  }

  private void addEventType(String id, int minutes) {
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes) VALUES (?, ?, '', ?)",
        id,
        id,
        minutes);
  }

  private void at(String iso) {
    NOW.set(java.time.OffsetDateTime.parse(iso).toInstant());
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private List<String> starts(String eventTypeId) throws IOException, InterruptedException {
    HttpResponse<String> response = get("/api/event-types/" + eventTypeId + "/slots");
    assertThat(response.statusCode()).isEqualTo(200);
    List<String> result = new ArrayList<>();
    for (JsonNode slot : mapper.readTree(response.body())) {
      result.add(slot.get("start").asText());
    }
    return result;
  }

  @Test
  void firstSlotOfDayStartsAtNineAndLastEndsExactlyAtEighteen()
      throws IOException, InterruptedException {
    JsonNode slots = mapper.readTree(get("/api/event-types/intro-call/slots").body());

    assertThat(slots.get(0).get("start").asText()).isEqualTo("2026-11-23T09:00:00+05:00");
    assertThat(slots.get(0).get("end").asText()).isEqualTo("2026-11-23T09:30:00+05:00");
    assertThat(slots.get(17).get("start").asText()).isEqualTo("2026-11-23T17:30:00+05:00");
    assertThat(slots.get(17).get("end").asText()).isEqualTo("2026-11-23T18:00:00+05:00");
    assertThat(slots.get(18).get("start").asText()).isEqualTo("2026-11-24T09:00:00+05:00");
  }

  @Test
  void startsAreEveryThirtyMinutesAndLongSlotMustFitBeforeEighteen()
      throws IOException, InterruptedException {
    List<String> starts = starts("consultation");

    assertThat(starts.subList(0, 3))
        .containsExactly(
            "2026-11-23T09:00:00+05:00", "2026-11-23T09:30:00+05:00", "2026-11-23T10:00:00+05:00");
    // 90 минут: последний старт 16:30, слот 16:30–18:00; 17:00 уже не помещается
    assertThat(starts)
        .contains("2026-11-23T16:30:00+05:00")
        .doesNotContain("2026-11-23T17:00:00+05:00");
    assertThat(starts.stream().filter(s -> s.startsWith("2026-11-23")).count()).isEqualTo(16);
  }

  @Test
  void windowCoversFourteenDaysStartingToday() throws IOException, InterruptedException {
    List<String> starts = starts("intro-call");

    assertThat(starts).hasSize(14 * 18);
    assertThat(starts.get(0)).startsWith("2026-11-23");
    assertThat(starts.get(starts.size() - 1)).isEqualTo("2026-12-06T17:30:00+05:00");
  }

  @Test
  void todaysPastSlotsAreHiddenAndStartMustBeStrictlyLaterThanNow()
      throws IOException, InterruptedException {
    at("2026-11-23T12:00:00+05:00");

    List<String> starts = starts("intro-call");

    assertThat(starts.get(0)).isEqualTo("2026-11-23T12:30:00+05:00");
    assertThat(starts).hasSize(14 * 18 - 7);
  }

  @Test
  void afterWorkingHoursTodayHasNoSlotsButWindowStillStartsToday()
      throws IOException, InterruptedException {
    at("2026-11-23T19:00:00+05:00");

    List<String> starts = starts("intro-call");

    assertThat(starts.get(0)).isEqualTo("2026-11-24T09:00:00+05:00");
    assertThat(starts).hasSize(13 * 18);
  }

  @Test
  void todayIsDeterminedInOwnerTimezoneNotUtc() throws IOException, InterruptedException {
    // 2026-11-23 20:00 UTC = 2026-11-24 01:00 в Almaty: «сегодня» уже 24-е
    at("2026-11-24T01:00:00+05:00");

    assertThat(starts("intro-call").get(0)).isEqualTo("2026-11-24T09:00:00+05:00");
  }

  @Test
  void unknownEventTypeGivesNotFound() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/api/event-types/nope/slots");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("\"code\":\"not_found\"");
  }
}
