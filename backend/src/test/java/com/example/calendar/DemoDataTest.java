package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** В профиле dev появляются демо-данные: два типа встречи. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:calendar-dev-test")
class DemoDataTest {

  @LocalServerPort private int port;

  @Test
  void devProfileSeedsTwoEventTypes() throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/event-types"))
            .GET()
            .build();
    String body =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();

    assertThat(body).contains("\"id\":\"intro-call\"").contains("\"id\":\"consultation\"");
  }
}
