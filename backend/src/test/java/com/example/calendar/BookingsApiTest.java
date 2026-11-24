package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

/** API-тест записи гостя: реальный HTTP, чистая БД, подменяемые часы. Пояс расписания: +05:00. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(BookingsApiTest.TestClockConfig.class)
class BookingsApiTest {

  private static final AtomicReference<Instant> NOW = new AtomicReference<>();

  @TestConfiguration
  static class TestClockConfig {
    @Bean
    @Primary
    Clock bookingsTestClock() {
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
    addEventType("intro-call", "Знакомство", 30);
    addEventType("consultation", "Консультация", 90);
    // 2026-11-23 08:00 в Asia/Almaty (понедельник)
    at("2026-11-23T08:00:00+05:00");
  }

  @AfterEach
  void cleanBookings() {
    jdbc.update("DELETE FROM bookings");
  }

  private void addEventType(String id, String title, int minutes) {
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes) VALUES (?, ?, '', ?)",
        id,
        title,
        minutes);
  }

  private void at(String iso) {
    NOW.set(OffsetDateTime.parse(iso).toInstant());
  }

  private HttpResponse<String> send(HttpRequest.Builder builder)
      throws IOException, InterruptedException {
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> book(String eventTypeId, String start, String name, String email)
      throws IOException, InterruptedException {
    String json =
        "{\"eventTypeId\":\"%s\",\"start\":\"%s\",\"guestName\":\"%s\",\"guestEmail\":\"%s\"}"
            .formatted(eventTypeId, start, name, email);
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bookings"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)));
  }

  private List<String> slotStarts(String eventTypeId) throws IOException, InterruptedException {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + port + "/api/event-types/" + eventTypeId + "/slots"))
                .GET());
    List<String> result = new ArrayList<>();
    for (JsonNode slot : mapper.readTree(response.body())) {
      result.add(slot.get("start").asText());
    }
    return result;
  }

  private int bookingCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
  }

  private void assertFieldError(HttpResponse<String> response, String field) {
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"code\":\"validation_error\"")
        .contains("\"fieldErrors\":{")
        .contains("\"" + field + "\":\"");
    assertThat(bookingCount()).isZero();
  }

  @Test
  void createsBookingAndServerComputesEnd() throws IOException, InterruptedException {
    HttpResponse<String> response =
        book("consultation", "2026-11-23T10:00:00+05:00", "Анна", "anna@example.com");

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode booking = mapper.readTree(response.body());
    assertThat(booking.get("id").asText()).isNotBlank();
    assertThat(booking.get("eventType").get("id").asText()).isEqualTo("consultation");
    assertThat(booking.get("eventType").get("title").asText()).isEqualTo("Консультация");
    assertThat(booking.get("start").asText()).isEqualTo("2026-11-23T10:00:00+05:00");
    assertThat(booking.get("end").asText()).isEqualTo("2026-11-23T11:30:00+05:00");
    assertThat(booking.get("guestName").asText()).isEqualTo("Анна");
    assertThat(booking.get("guestEmail").asText()).isEqualTo("anna@example.com");
    assertThat(bookingCount()).isEqualTo(1);
  }

  @Test
  void acceptsStartInAnotherOffsetAndReturnsOwnerTimezone()
      throws IOException, InterruptedException {
    // 05:00 UTC = 10:00 в Almaty
    HttpResponse<String> response =
        book("intro-call", "2026-11-23T05:00:00Z", "Анна", "anna@example.com");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(mapper.readTree(response.body()).get("start").asText())
        .isEqualTo("2026-11-23T10:00:00+05:00");
  }

  @Test
  void rejectsEmptyGuestName() throws IOException, InterruptedException {
    assertFieldError(
        book("intro-call", "2026-11-23T10:00:00+05:00", "   ", "anna@example.com"), "guestName");
  }

  @Test
  void rejectsMalformedEmail() throws IOException, InterruptedException {
    assertFieldError(
        book("intro-call", "2026-11-23T10:00:00+05:00", "Анна", "not-an-email"), "guestEmail");
  }

  @Test
  void rejectsStartOffThirtyMinuteGrid() throws IOException, InterruptedException {
    assertFieldError(
        book("intro-call", "2026-11-23T10:15:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void rejectsStartBeforeWorkingHours() throws IOException, InterruptedException {
    assertFieldError(
        book("intro-call", "2026-11-23T08:30:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void rejectsStartBeyondBookingWindow() throws IOException, InterruptedException {
    // окно: 23 ноября – 6 декабря; 7 декабря уже вне окна
    assertFieldError(
        book("intro-call", "2026-12-07T09:00:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void acceptsLastSlotOfWindow() throws IOException, InterruptedException {
    assertThat(
            book("intro-call", "2026-12-06T17:30:00+05:00", "Анна", "anna@example.com")
                .statusCode())
        .isEqualTo(201);
  }

  @Test
  void rejectsStartInThePast() throws IOException, InterruptedException {
    at("2026-11-23T12:00:00+05:00");

    assertFieldError(
        book("intro-call", "2026-11-23T11:00:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void rejectsStartEqualToNow() throws IOException, InterruptedException {
    at("2026-11-23T12:00:00+05:00");

    assertFieldError(
        book("intro-call", "2026-11-23T12:00:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void rejectsBookingThatEndsAfterEighteen() throws IOException, InterruptedException {
    // 90 минут со старта 17:00 заканчиваются в 18:30
    assertFieldError(
        book("consultation", "2026-11-23T17:00:00+05:00", "Анна", "anna@example.com"), "start");
  }

  @Test
  void acceptsBookingThatEndsExactlyAtEighteen() throws IOException, InterruptedException {
    assertThat(
            book("consultation", "2026-11-23T16:30:00+05:00", "Анна", "anna@example.com")
                .statusCode())
        .isEqualTo(201);
  }

  @Test
  void reportsSeveralFieldErrorsAtOnce() throws IOException, InterruptedException {
    HttpResponse<String> response = book("intro-call", "2026-11-23T10:15:00+05:00", "", "x");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"guestName\":\"")
        .contains("\"guestEmail\":\"")
        .contains("\"start\":\"");
  }

  @Test
  void unknownEventTypeGivesNotFound() throws IOException, InterruptedException {
    HttpResponse<String> response =
        book("nope", "2026-11-23T10:00:00+05:00", "Анна", "anna@example.com");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("\"code\":\"not_found\"");
    assertThat(bookingCount()).isZero();
  }

  @Test
  void bookedSlotDisappearsFromSlotsOfEveryEventTypeButNeighboursStay()
      throws IOException, InterruptedException {
    // Встреча 10:00–11:30 (консультация) занимает время и для другого типа
    book("consultation", "2026-11-23T10:00:00+05:00", "Анна", "anna@example.com");

    List<String> intro = slotStarts("intro-call");
    assertThat(intro)
        .doesNotContain(
            "2026-11-23T10:00:00+05:00", "2026-11-23T10:30:00+05:00", "2026-11-23T11:00:00+05:00")
        .contains("2026-11-23T09:30:00+05:00", "2026-11-23T11:30:00+05:00");
    assertThat(slotStarts("consultation")).doesNotContain("2026-11-23T10:00:00+05:00");
  }

  private static final String CONFLICT_MESSAGE = "Это время уже занято, выберите другой слот";

  private void assertConflict(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body())
        .contains("\"code\":\"conflict\"")
        .contains("\"message\":\"" + CONFLICT_MESSAGE + "\"");
  }

  @Test
  void adjacentBookingsAreAllowedOnBothSides() throws IOException, InterruptedException {
    // Занято [10:00, 11:30); встречи [09:30, 10:00) и [11:30, 12:00) только касаются границ
    assertThat(
            book("consultation", "2026-11-23T10:00:00+05:00", "Анна", "a@example.com").statusCode())
        .isEqualTo(201);
    assertThat(
            book("intro-call", "2026-11-23T11:30:00+05:00", "Борис", "b@example.com").statusCode())
        .isEqualTo(201);
    assertThat(
            book("intro-call", "2026-11-23T09:30:00+05:00", "Глеб", "g@example.com").statusCode())
        .isEqualTo(201);
    assertThat(bookingCount()).isEqualTo(3);
  }

  @Test
  void partialOverlapGivesConflict() throws IOException, InterruptedException {
    book("consultation", "2026-11-23T10:00:00+05:00", "Анна", "a@example.com");

    // [11:00, 11:30) попадает в хвост [10:00, 11:30)
    assertConflict(book("intro-call", "2026-11-23T11:00:00+05:00", "Борис", "b@example.com"));
    // [09:00, 10:30) заходит в начало
    assertConflict(book("consultation", "2026-11-23T09:00:00+05:00", "Вера", "v@example.com"));
    assertThat(bookingCount()).isEqualTo(1);
  }

  @Test
  void overlapWithBookingOfAnotherTypeGivesConflict() throws IOException, InterruptedException {
    book("intro-call", "2026-11-23T10:00:00+05:00", "Анна", "a@example.com");

    assertConflict(book("consultation", "2026-11-23T09:30:00+05:00", "Борис", "b@example.com"));
    assertConflict(book("intro-call", "2026-11-23T10:00:00+05:00", "Вера", "v@example.com"));
    assertThat(bookingCount()).isEqualTo(1);
  }

  @Test
  void twoConcurrentRequestsForOverlappingTimeGiveOneCreatedAndOneConflict() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (String type : List.of("intro-call", "consultation")) {
        results.add(
            pool.submit(
                () -> {
                  barrier.await();
                  return book(type, "2026-11-23T10:00:00+05:00", "Гость", "g@example.com")
                      .statusCode();
                }));
      }
      List<Integer> codes = new ArrayList<>();
      for (Future<Integer> f : results) {
        codes.add(f.get());
      }
      assertThat(codes).containsExactlyInAnyOrder(201, 409);
      assertThat(bookingCount()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void manyConcurrentRoundsNeverCreateOverlappingBookings() throws Exception {
    // Одиночная гонка двух запросов без блокировки проходит случайно; повторы и 8 потоков на
    // раунд делают отсутствие блокировки заметным
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int round = 0; round < 15; round++) {
        jdbc.update("DELETE FROM bookings");
        String start = "2026-11-23T%02d:%02d:00+05:00".formatted(9 + round / 2, round % 2 * 30);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          String type = i % 2 == 0 ? "intro-call" : "consultation";
          results.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    return book(type, start, "Гость", "g@example.com").statusCode();
                  }));
        }
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) {
          codes.add(f.get());
        }
        assertThat(codes.stream().filter(c -> c == 201).count())
            .as("раунд %d, старт %s, коды %s", round, start, codes)
            .isEqualTo(1);
        assertThat(codes.stream().filter(c -> c == 409).count())
            .as("раунд %d, коды %s", round, codes)
            .isEqualTo(threads - 1);
        assertThat(bookingCount()).as("раунд %d", round).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void bookingWaitsForCalendarLockHeldByAnotherTransaction() throws Exception {
    // Детерминированная проверка: пока чужая транзакция держит calendar_lock, запись обязана
    // ждать. Без SELECT ... FOR UPDATE запрос завершился бы сразу
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection holder = jdbc.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      try (Statement st = holder.createStatement()) {
        st.execute("SELECT id FROM calendar_lock WHERE id = 1 FOR UPDATE");
      }
      Future<Integer> pending =
          pool.submit(
              () ->
                  book("intro-call", "2026-11-23T10:00:00+05:00", "Анна", "a@example.com")
                      .statusCode());
      assertThatThrownBy(() -> pending.get(500, TimeUnit.MILLISECONDS))
          .as("запись должна ждать освобождения calendar_lock")
          .isInstanceOf(TimeoutException.class);
      assertThat(bookingCount()).isZero();
      holder.rollback();
      assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo(201);
      assertThat(bookingCount()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
