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

/**
 * Бэкенд отдаёт собранный фронтенд: маршруты React Router открываются напрямую, а неизвестные пути
 * API и отсутствующие файлы остаются 404. В тестах вместо сборки лежит
 * src/test/resources/static/index.html.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpaFallbackTest {

  @LocalServerPort private int port;

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void rootServesIndex() throws Exception {
    HttpResponse<String> response = get("/");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("spa-test-index");
  }

  @Test
  void clientRoutesServeIndex() throws Exception {
    for (String path : new String[] {"/book", "/owner", "/book/confirmation"}) {
      HttpResponse<String> response = get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.body()).as(path).contains("spa-test-index");
    }
  }

  @Test
  void apiStillAnswersJson() throws Exception {
    HttpResponse<String> response = get("/api/health");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"ok\"");
  }

  @Test
  void unknownApiPathIsNotFoundInErrorFormat() throws Exception {
    HttpResponse<String> response = get("/api/no-such-endpoint");
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).contains("\"code\":\"not_found\"");
  }

  @Test
  void missingAssetIsNotFound() throws Exception {
    assertThat(get("/assets/missing.js").statusCode()).isEqualTo(404);
  }
}
