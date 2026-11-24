package com.example.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** Тестовые настройки не должны затенять основной application.properties. */
@SpringBootTest
class MainConfigurationTest {

  @Autowired private Environment environment;

  @Test
  void mainPropertiesAreVisibleInTests() {
    assertThat(environment.getProperty("calendar.schedule.window-days")).isEqualTo("14");
    assertThat(environment.getProperty("calendar.schedule.work-end")).isEqualTo("18:00");
    assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("always");
  }

  @Test
  void testsUseInMemoryDatabase() {
    assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
  }
}
