package com.example.calendar.slot;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlotSchedule.class)
class SlotConfig {

  /** Внедряемые часы: тесты подменяют этот бин. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  SlotCalculator slotCalculator(SlotSchedule schedule) {
    return new SlotCalculator(schedule);
  }
}
