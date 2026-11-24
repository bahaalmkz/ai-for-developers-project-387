package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Единый формат ошибок: поля в 400, читаемые ответы на мусор, чистые 404 и 409. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ErrorFormatApiTest {

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("DELETE FROM bookings");
    jdbc.update("DELETE FROM event_types");
  }

  private HttpResponse<String> post(String path, String json)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private void assertFieldErrors(HttpResponse<String> response, String... fields) {
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"code\":\"validation_error\"");
    for (String field : fields) {
      assertThat(response.body()).contains("\"" + field + "\":\"");
    }
  }

  private void assertPlain400(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"code\":").contains("\"message\":");
    assertThat(response.body()).doesNotContain("internal_error");
  }

  @Test
  void bookingWithEmptyObjectReportsAllRequiredFields() throws IOException, InterruptedException {
    assertFieldErrors(
        post("/api/bookings", "{}"), "eventTypeId", "start", "guestName", "guestEmail");
  }

  @Test
  void bookingWithoutGuestEmailReportsThatField() throws IOException, InterruptedException {
    assertFieldErrors(
        post(
            "/api/bookings",
            "{\"eventTypeId\":\"a\",\"start\":\"2030-01-01T09:00:00+05:00\",\"guestName\":\"Аня\"}"),
        "guestEmail");
  }

  @Test
  void eventTypeWithEmptyObjectReportsRequiredFieldsButNotDescription()
      throws IOException, InterruptedException {
    HttpResponse<String> response = post("/api/event-types", "{}");

    assertFieldErrors(response, "id", "title", "durationMinutes");
    assertThat(response.body()).doesNotContain("\"description\":\"");
  }

  @Test
  void eventTypeWithoutDescriptionIsCreatedWithEmptyDescription()
      throws IOException, InterruptedException {
    HttpResponse<String> response =
        post("/api/event-types", "{\"id\":\"a\",\"title\":\"Т\",\"durationMinutes\":30}");

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"description\":\"\"");
  }

  @Test
  void unparsableJsonGives400() throws IOException, InterruptedException {
    assertPlain400(post("/api/bookings", "not json"));
    assertPlain400(post("/api/event-types", "not json"));
  }

  @Test
  void wrongValueTypesGive400() throws IOException, InterruptedException {
    assertPlain400(
        post(
            "/api/bookings",
            "{\"eventTypeId\":\"a\",\"start\":\"garbage\",\"guestName\":\"Аня\","
                + "\"guestEmail\":\"a@b.c\"}"));
    assertPlain400(
        post(
            "/api/event-types",
            "{\"id\":\"a\",\"title\":\"Т\",\"description\":\"\",\"durationMinutes\":\"x\"}"));
  }

  @Test
  void notFoundHasRussianMessageAndNoFieldErrors() throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/event-types/no/slots"))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).doesNotContain("fieldErrors").doesNotContain("Not Found");
  }

  @Test
  void conflictHasNoFieldErrors() throws IOException, InterruptedException {
    String json = "{\"id\":\"a\",\"title\":\"Т\",\"description\":\"\",\"durationMinutes\":30}";
    post("/api/event-types", json);

    HttpResponse<String> response = post("/api/event-types", json);

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).doesNotContain("fieldErrors");
  }

  @Test
  void emailWithEmptyDomainLabelIsRejected() throws IOException, InterruptedException {
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes)"
            + " VALUES ('a', 'Т', '', 30)");
    assertFieldErrors(
        post(
            "/api/bookings",
            "{\"eventTypeId\":\"a\",\"start\":\"2030-01-01T09:00:00+05:00\","
                + "\"guestName\":\"Аня\",\"guestEmail\":\"a@b..c\"}"),
        "guestEmail");
  }
}
