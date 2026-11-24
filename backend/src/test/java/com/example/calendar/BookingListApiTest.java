package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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

/** API-тест списка предстоящих встреч владельца: реальный HTTP, чистая БД, подменяемые часы. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(BookingListApiTest.TestClockConfig.class)
class BookingListApiTest {

  private static final AtomicReference<Instant> NOW = new AtomicReference<>();

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock bookingListTestClock() {
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
    jdbc.update("DELETE FROM bookings");
    jdbc.update("DELETE FROM event_types");
    addEventType("intro-call", "Знакомство");
    addEventType("consultation", "Консультация");
    at("2026-11-23T12:00:00+05:00");
  }

  @AfterEach
  void cleanBookings() {
    jdbc.update("DELETE FROM bookings");
  }

  private void addEventType(String id, String title) {
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes) VALUES (?, ?, '', 30)",
        id,
        title);
  }

  private void at(String iso) {
    NOW.set(OffsetDateTime.parse(iso).toInstant());
  }

  private void addBooking(String eventTypeId, String start, String end, String name) {
    jdbc.update(
        "INSERT INTO bookings (id, event_type_id, start_at, end_at, guest_name, guest_email)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        "id-" + name,
        eventTypeId,
        OffsetDateTime.parse(start),
        OffsetDateTime.parse(end),
        name,
        "guest@example.com");
  }

  private HttpResponse<String> list() throws IOException, InterruptedException {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  private List<String> guestNames(HttpResponse<String> response) {
    List<String> names = new ArrayList<>();
    for (JsonNode booking : mapper.readTree(response.body())) {
      names.add(booking.get("guestName").asText());
    }
    return names;
  }

  @Test
  void emptyListWhenNoBookings() throws IOException, InterruptedException {
    HttpResponse<String> response = list();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(mapper.readTree(response.body()).isArray()).isTrue();
    assertThat(guestNames(response)).isEmpty();
  }

  @Test
  void listsUpcomingBookingsOfAllTypesInAscendingStartOrder()
      throws IOException, InterruptedException {
    addBooking("consultation", "2026-11-24T10:00:00+05:00", "2026-11-24T11:30:00+05:00", "Поздняя");
    addBooking("intro-call", "2026-11-23T14:00:00+05:00", "2026-11-23T14:30:00+05:00", "Ранняя");

    HttpResponse<String> response = list();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(guestNames(response)).containsExactly("Ранняя", "Поздняя");
  }

  @Test
  void entryCarriesTypeTitleIntervalAndGuest() throws IOException, InterruptedException {
    addBooking("consultation", "2026-11-24T10:00:00+05:00", "2026-11-24T11:30:00+05:00", "Анна");

    JsonNode entry = mapper.readTree(list().body()).get(0);

    assertThat(entry.get("eventType").get("id").asText()).isEqualTo("consultation");
    assertThat(entry.get("eventType").get("title").asText()).isEqualTo("Консультация");
    assertThat(entry.get("start").asText()).isEqualTo("2026-11-24T10:00:00+05:00");
    assertThat(entry.get("end").asText()).isEqualTo("2026-11-24T11:30:00+05:00");
    assertThat(entry.get("guestName").asText()).isEqualTo("Анна");
    assertThat(entry.get("guestEmail").asText()).isEqualTo("guest@example.com");
  }

  @Test
  void hidesPastBookingsButKeepsOneInProgress() throws IOException, InterruptedException {
    addBooking("intro-call", "2026-11-23T10:00:00+05:00", "2026-11-23T10:30:00+05:00", "Прошла");
    // идёт сейчас: начало до 12:00, конец позже
    addBooking("consultation", "2026-11-23T11:30:00+05:00", "2026-11-23T13:00:00+05:00", "Идёт");
    addBooking("intro-call", "2026-11-23T15:00:00+05:00", "2026-11-23T15:30:00+05:00", "Будет");

    assertThat(guestNames(list())).containsExactly("Идёт", "Будет");
  }

  @Test
  void hidesBookingThatEndsExactlyNow() throws IOException, InterruptedException {
    addBooking("intro-call", "2026-11-23T11:30:00+05:00", "2026-11-23T12:00:00+05:00", "Конец");

    assertThat(guestNames(list())).isEmpty();
  }

  @Test
  void bookingBecomesPastWhenClockMovesForward() throws IOException, InterruptedException {
    addBooking("intro-call", "2026-11-23T15:00:00+05:00", "2026-11-23T15:30:00+05:00", "Будет");
    assertThat(guestNames(list())).containsExactly("Будет");

    at("2026-11-23T15:30:00+05:00");

    assertThat(guestNames(list())).isEmpty();
  }
}
