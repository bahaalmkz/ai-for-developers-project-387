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

/** API-тест списка типов встреч: реальный HTTP, чистая БД в памяти. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EventTypesApiTest {

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("DELETE FROM event_types");
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void returnsEmptyListWhenNoEventTypes() throws IOException, InterruptedException {
    HttpResponse<String> response = get("/api/event-types");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");
  }

  @Test
  void returnsStoredEventTypes() throws IOException, InterruptedException {
    jdbc.update(
        "INSERT INTO event_types (id, title, description, duration_minutes) VALUES (?, ?, ?, ?)",
        "intro-call",
        "Знакомство",
        "Короткий звонок",
        30);

    HttpResponse<String> response = get("/api/event-types");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"id\":\"intro-call\"")
        .contains("\"title\":\"Знакомство\"")
        .contains("\"description\":\"Короткий звонок\"")
        .contains("\"durationMinutes\":30");
  }
}
