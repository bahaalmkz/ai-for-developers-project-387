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

/** API-тест создания типа встречи: реальный HTTP, чистая БД в памяти. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CreateEventTypeApiTest {

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("DELETE FROM event_types");
  }

  private HttpResponse<String> post(String json) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/event-types"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String body(String id, String title, String description, Object duration) {
    return "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"durationMinutes\":%s}"
        .formatted(id, title, description, duration);
  }

  private void assertFieldError(HttpResponse<String> response, String field) {
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"code\":\"validation_error\"")
        .contains("\"fieldErrors\":{")
        .contains("\"" + field + "\":\"");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_types", Integer.class)).isZero();
  }

  @Test
  void createsEventTypeAndReturnsIt() throws IOException, InterruptedException {
    HttpResponse<String> response = post(body("intro-call-2", "Знакомство", "Короткий звонок", 30));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body())
        .contains("\"id\":\"intro-call-2\"")
        .contains("\"title\":\"Знакомство\"")
        .contains("\"description\":\"Короткий звонок\"")
        .contains("\"durationMinutes\":30");
  }

  @Test
  void createdEventTypeAppearsInList() throws IOException, InterruptedException {
    post(body("intro", "Знакомство", "", 480));

    HttpRequest get =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/event-types"))
            .GET()
            .build();
    HttpResponse<String> list =
        HttpClient.newHttpClient().send(get, HttpResponse.BodyHandlers.ofString());

    assertThat(list.body()).contains("\"id\":\"intro\"").contains("\"durationMinutes\":480");
  }

  @Test
  void rejectsEmptyId() throws IOException, InterruptedException {
    assertFieldError(post(body("", "Название", "", 30)), "id");
  }

  @Test
  void rejectsIdWithUppercaseOrSpecialCharacters() throws IOException, InterruptedException {
    assertFieldError(post(body("Intro", "Название", "", 30)), "id");
    assertFieldError(post(body("intro_call", "Название", "", 30)), "id");
  }

  @Test
  void rejectsBlankTitle() throws IOException, InterruptedException {
    assertFieldError(post(body("a", "   ", "", 30)), "title");
  }

  @Test
  void rejectsTooLongTitle() throws IOException, InterruptedException {
    assertFieldError(post(body("a", "x".repeat(101), "", 30)), "title");
  }

  @Test
  void acceptsTitleOfExactlyMaxLength() throws IOException, InterruptedException {
    assertThat(post(body("a", "x".repeat(100), "", 30)).statusCode()).isEqualTo(201);
  }

  @Test
  void rejectsTooLongDescription() throws IOException, InterruptedException {
    assertFieldError(post(body("a", "Название", "x".repeat(501), 30)), "description");
  }

  @Test
  void acceptsDescriptionOfExactlyMaxLength() throws IOException, InterruptedException {
    assertThat(post(body("a", "Название", "x".repeat(500), 30)).statusCode()).isEqualTo(201);
  }

  @Test
  void rejectsDurationNotMultipleOf30() throws IOException, InterruptedException {
    assertFieldError(post(body("a", "Название", "", 45)), "durationMinutes");
  }

  @Test
  void rejectsDurationOutOfRange() throws IOException, InterruptedException {
    assertFieldError(post(body("a", "Название", "", 0)), "durationMinutes");
    assertFieldError(post(body("a", "Название", "", 510)), "durationMinutes");
  }

  @Test
  void reportsErrorsOfSeveralFieldsAtOnce() throws IOException, InterruptedException {
    HttpResponse<String> response = post(body("BAD", "", "", 7));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body())
        .contains("\"id\":\"")
        .contains("\"title\":\"")
        .contains("\"durationMinutes\":\"");
  }

  @Test
  void rejectsDuplicateIdWith409AndKeepsOriginal() throws IOException, InterruptedException {
    post(body("intro", "Первый", "", 30));

    HttpResponse<String> response = post(body("intro", "Второй", "", 60));

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).contains("\"code\":\"conflict\"").contains("intro");
    assertThat(
            jdbc.queryForObject("SELECT title FROM event_types WHERE id = 'intro'", String.class))
        .isEqualTo("Первый");
  }
}
