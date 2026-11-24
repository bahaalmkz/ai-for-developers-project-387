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
import tools.jackson.databind.node.ObjectNode;

/**
 * API-тест темы разговора (спецификация #8): основной, негативный и соседний сценарии. Реальный
 * HTTP, чистая БД в памяти, подменяемые часы. Пояс расписания: +05:00.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(GuestTopicApiTest.TestClockConfig.class)
class GuestTopicApiTest {

  private static final AtomicReference<Instant> NOW = new AtomicReference<>();
  private static final String START = "2026-11-23T10:00:00+05:00";

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock guestTopicTestClock() {
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
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes)"
            + " VALUES ('intro-call', 'Знакомство', '', 30)");
    NOW.set(OffsetDateTime.parse("2026-11-23T08:00:00+05:00").toInstant());
  }

  @AfterEach
  void cleanBookings() {
    jdbc.update("DELETE FROM bookings");
  }

  /** Запись; {@code topic == null} — поле guestTopic в запросе не передаётся. */
  private HttpResponse<String> book(String start, String topic)
      throws IOException, InterruptedException {
    ObjectNode body = mapper.createObjectNode();
    body.put("eventTypeId", "intro-call");
    body.put("start", start);
    body.put("guestName", "Анна");
    body.put("guestEmail", "anna@example.com");
    if (topic != null) {
      body.put("guestTopic", topic);
    }
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
  }

  private JsonNode upcoming() throws IOException, InterruptedException {
    HttpResponse<String> response =
        send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings")));
    assertThat(response.statusCode()).isEqualTo(200);
    return mapper.readTree(response.body());
  }

  private HttpResponse<String> send(HttpRequest.Builder builder)
      throws IOException, InterruptedException {
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private int bookingCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
  }

  private String storedTopic() {
    return jdbc.queryForObject("SELECT guest_topic FROM bookings", String.class);
  }

  // --- Сценарий 1, основной: запись с темой ---

  @Test
  void bookingWithTopicReturnsTrimmedTopicAndStoresIt() throws IOException, InterruptedException {
    HttpResponse<String> response = book(START, "  Обсудить переезд на новый тариф  ");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(mapper.readTree(response.body()).get("guestTopic").asText())
        .isEqualTo("Обсудить переезд на новый тариф");
    assertThat(storedTopic()).isEqualTo("Обсудить переезд на новый тариф");
  }

  @Test
  void ownerListShowsTopicOfBooking() throws IOException, InterruptedException {
    book(START, "Обсудить переезд на новый тариф");

    JsonNode list = upcoming();

    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("guestTopic").asText()).isEqualTo("Обсудить переезд на новый тариф");
  }

  @Test
  void topicOfExactlyTwoHundredCharactersIsAccepted() throws IOException, InterruptedException {
    String topic = "т".repeat(200);

    HttpResponse<String> response = book(START, topic);

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(mapper.readTree(response.body()).get("guestTopic").asText()).isEqualTo(topic);
  }

  // --- Сценарий 2, негативный: занятое время и слишком длинная тема ---

  @Test
  void busyTimeWithAnotherTopicGivesConflictAndNoSecondBooking()
      throws IOException, InterruptedException {
    assertThat(book(START, "Первая тема").statusCode()).isEqualTo(201);

    HttpResponse<String> second = book(START, "Другая тема");

    assertThat(second.statusCode()).isEqualTo(409);
    assertThat(second.body()).contains("\"code\":\"conflict\"");
    assertThat(bookingCount()).isEqualTo(1);
    assertThat(storedTopic()).isEqualTo("Первая тема");
  }

  @Test
  void topicLongerThanTwoHundredCharactersGivesFieldError()
      throws IOException, InterruptedException {
    HttpResponse<String> response = book(START, "т".repeat(201));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"code\":\"validation_error\"")
        .contains("\"guestTopic\":\"");
    assertThat(bookingCount()).isZero();
  }

  /** Регрессия: тема с переводом строки ломала строку «Тема: …» у владельца. */
  @Test
  void topicWithLineBreakGivesFieldErrorAndNoBooking() throws IOException, InterruptedException {
    HttpResponse<String> lf = book(START, "строка1\nстрока2");
    HttpResponse<String> cr = book(START, "строка1\rстрока2");

    assertThat(lf.statusCode()).isEqualTo(400);
    assertThat(lf.body())
        .contains("\"code\":\"validation_error\"")
        .contains("\"guestTopic\":\"Тема — одна строка, без переводов строк\"");
    assertThat(cr.statusCode()).isEqualTo(400);
    assertThat(cr.body()).contains("\"guestTopic\":");
    assertThat(bookingCount()).isZero();
  }

  @Test
  void blankTopicIsNotAnErrorAndIsStoredEmpty() throws IOException, InterruptedException {
    HttpResponse<String> response = book(START, "    ");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(mapper.readTree(response.body()).get("guestTopic").asText()).isEmpty();
    assertThat(storedTopic()).isEmpty();
  }

  // --- Сценарий 3, соседний: обычная запись без темы работает как раньше ---

  @Test
  void bookingWithoutTopicIsCreatedWithEmptyTopic() throws IOException, InterruptedException {
    HttpResponse<String> response = book(START, null);

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode booking = mapper.readTree(response.body());
    assertThat(booking.get("guestName").asText()).isEqualTo("Анна");
    assertThat(booking.get("guestTopic").asText()).isEmpty();
    assertThat(upcoming().get(0).get("guestTopic").asText()).isEmpty();
  }

  @Test
  void bookingStoredWithoutTopicColumnValueIsListedWithEmptyTopic()
      throws IOException, InterruptedException {
    // Так выглядит встреча, созданная до появления темы: guest_topic берёт значение по умолчанию
    jdbc.update(
        "INSERT INTO bookings (id, event_type_id, start_at, end_at, guest_name, guest_email)"
            + " VALUES ('old', 'intro-call', ?, ?, 'Борис', 'boris@example.com')",
        OffsetDateTime.parse("2026-11-23T11:00:00+05:00"),
        OffsetDateTime.parse("2026-11-23T11:30:00+05:00"));

    JsonNode list = upcoming();

    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("guestName").asText()).isEqualTo("Борис");
    assertThat(list.get(0).get("guestTopic").asText()).isEmpty();
  }
}
